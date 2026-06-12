package com.phantomguard.dataplane.upstream;

import com.phantomguard.dataplane.config.DataPlaneProperties;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.net.InetSocketAddress;
import java.time.Duration;

/**
 * Fully asynchronous forwarder to the upstream recursive resolver.
 *
 * <p>Queries go out over UDP first (one ephemeral Netty datagram channel per
 * in-flight query — cheap, isolated, no correlation table needed). If the
 * upstream sets the TC (truncated) bit, the query is transparently retried
 * over TCP with RFC 1035 2-byte length framing so large answers (DNSSEC,
 * HTTPS records) survive intact.
 */
@Component
public class UpstreamResolver {

    private static final Logger log = LoggerFactory.getLogger(UpstreamResolver.class);
    private static final int TC_BIT_MASK = 0x02; // bit 1 of byte 2

    private final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(2);
    private final InetSocketAddress upstreamAddress;
    private final Duration timeout;

    public UpstreamResolver(DataPlaneProperties properties) {
        this.upstreamAddress = new InetSocketAddress(
                properties.getUpstreamHost(), properties.getUpstreamPort());
        this.timeout = Duration.ofMillis(properties.getUpstreamTimeoutMs());
    }

    /** Forwards the raw wire-format query and resolves with the raw response. */
    public Mono<byte[]> forward(byte[] query) {
        return forwardUdp(query)
                .flatMap(response -> isTruncated(response) ? forwardTcp(query) : Mono.just(response))
                .timeout(timeout);
    }

    private static boolean isTruncated(byte[] response) {
        return response.length > 3 && (response[2] & TC_BIT_MASK) != 0;
    }

    private Mono<byte[]> forwardUdp(byte[] query) {
        return Mono.create(sink -> {
            Bootstrap bootstrap = new Bootstrap()
                    .group(eventLoopGroup)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_RCVBUF, 65535)
                    .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
                            sink.success(readBytes(packet.content()));
                            ctx.close();
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            sink.error(cause);
                            ctx.close();
                        }
                    });

            bootstrap.connect(upstreamAddress).addListener(future -> {
                if (!future.isSuccess()) {
                    sink.error(future.cause());
                    return;
                }
                Channel channel = ((io.netty.channel.ChannelFuture) future).channel();
                registerCleanup(sink, channel);
                channel.writeAndFlush(new DatagramPacket(
                                Unpooled.wrappedBuffer(query), upstreamAddress))
                        .addListener(writeFuture -> {
                            if (!writeFuture.isSuccess()) {
                                sink.error(writeFuture.cause());
                            }
                        });
            });
        });
    }

    private Mono<byte[]> forwardTcp(byte[] query) {
        log.debug("UDP answer truncated, retrying over TCP");
        return Mono.create(sink -> {
            Bootstrap bootstrap = new Bootstrap()
                    .group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2))
                                    .addLast(new LengthFieldPrepender(2))
                                    .addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                                            sink.success(readBytes(msg));
                                            ctx.close();
                                        }

                                        @Override
                                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                            sink.error(cause);
                                            ctx.close();
                                        }
                                    });
                        }
                    });

            bootstrap.connect(upstreamAddress).addListener(future -> {
                if (!future.isSuccess()) {
                    sink.error(future.cause());
                    return;
                }
                Channel channel = ((io.netty.channel.ChannelFuture) future).channel();
                registerCleanup(sink, channel);
                channel.writeAndFlush(Unpooled.wrappedBuffer(query));
            });
        });
    }

    private static void registerCleanup(MonoSink<byte[]> sink, Channel channel) {
        sink.onDispose(() -> {
            if (channel.isOpen()) {
                channel.close();
            }
        });
    }

    private static byte[] readBytes(ByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    @PreDestroy
    void shutdown() {
        eventLoopGroup.shutdownGracefully();
    }
}
