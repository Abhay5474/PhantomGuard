package com.phantomguard.dataplane.dot;

import com.phantomguard.dataplane.config.DataPlaneProperties;
import com.phantomguard.dataplane.engine.DnsResolutionService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.DomainWildcardMappingBuilder;
import io.netty.util.Mapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * DNS-over-TLS (RFC 7858) listener — the transport Android's "Private DNS"
 * setting speaks natively. Per-connection pipeline:
 *
 * <pre>
 *   SniHandler (TLS + SNI capture)
 *     -> SniClientIdExtractor   ({clientId}.dns.yourdomain.com -> channel attr)
 *     -> LengthFieldBasedFrameDecoder (2-byte RFC 7858 framing, inbound)
 *     -> LengthFieldPrepender         (outbound framing)
 *     -> IdleStateHandler / DotQueryHandler
 * </pre>
 */
@Component
public class DotServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DotServer.class);
    private static final int MAX_DNS_MESSAGE = 65535;
    private static final int IDLE_TIMEOUT_SECONDS = 120;

    private final DataPlaneProperties properties;
    private final DnsResolutionService resolutionService;
    private final TlsContextFactory tlsContextFactory;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private volatile boolean running;

    public DotServer(DataPlaneProperties properties,
                     DnsResolutionService resolutionService,
                     TlsContextFactory tlsContextFactory) {
        this.properties = properties;
        this.resolutionService = resolutionService;
        this.tlsContextFactory = tlsContextFactory;
    }

    @Override
    public void start() {
        if (!properties.isDotEnabled()) {
            log.info("DoT listener disabled by configuration");
            running = true;
            return;
        }

        SslContext sslContext = tlsContextFactory.createServerContext();
        Mapping<String, SslContext> sniMapping = new DomainWildcardMappingBuilder<>(sslContext).build();

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new SniHandler(sniMapping))
                                .addLast(new SniClientIdExtractor(properties.getDnsDomainSuffix()))
                                .addLast(new LengthFieldBasedFrameDecoder(MAX_DNS_MESSAGE, 0, 2, 0, 2))
                                .addLast(new LengthFieldPrepender(2))
                                .addLast(new IdleStateHandler(0, 0, IDLE_TIMEOUT_SECONDS))
                                .addLast(new DotQueryHandler(resolutionService));
                    }
                });

        try {
            serverChannel = bootstrap.bind(properties.getDotPort()).sync().channel();
            running = true;
            log.info("DoT listener started on port {} (suffix: *.{})",
                    properties.getDotPort(), properties.getDnsDomainSuffix());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while binding DoT listener", e);
        }
    }

    @Override
    public void stop() {
        running = false;
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
        log.info("DoT listener stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
