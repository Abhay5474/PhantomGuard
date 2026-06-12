package com.phantomguard.dataplane.dot;

import com.phantomguard.common.dns.DnsParseException;
import com.phantomguard.dataplane.engine.DnsResolutionService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Terminal handler of the DoT pipeline. By the time a message reaches this
 * handler the TLS record layer and the RFC 7858 2-byte length framing have
 * already been stripped, so {@code msg} is exactly one wire-format DNS query.
 *
 * <p>Resolution is dispatched to the shared reactive pipeline; the Netty event
 * loop is never blocked. Responses are written back with the length prefix
 * re-applied by the outbound {@code LengthFieldPrepender}. A single DoT
 * connection may pipeline many queries (Android's Private DNS does), so the
 * channel stays open until the peer closes it or an unrecoverable error occurs.
 */
public class DotQueryHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final Logger log = LoggerFactory.getLogger(DotQueryHandler.class);

    private final DnsResolutionService resolutionService;

    public DotQueryHandler(DnsResolutionService resolutionService) {
        this.resolutionService = resolutionService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        String clientId = ctx.channel().attr(SniClientIdExtractor.CLIENT_ID_ATTR).get();
        if (clientId == null) {
            // SNI never produced a routable clientId; defensive close.
            ctx.close();
            return;
        }

        byte[] query = new byte[msg.readableBytes()];
        msg.readBytes(query);

        resolutionService.resolve(clientId, query).subscribe(
                response -> {
                    if (ctx.channel().isActive()) {
                        ctx.writeAndFlush(Unpooled.wrappedBuffer(response));
                    }
                },
                error -> {
                    if (error instanceof DnsParseException) {
                        log.debug("Malformed DoT query from {}: {}",
                                ctx.channel().remoteAddress(), error.getMessage());
                    } else {
                        log.warn("DoT resolution error for clientId={}: {}", clientId, error.toString());
                    }
                    ctx.close();
                });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.debug("DoT channel error from {}: {}", ctx.channel().remoteAddress(), cause.toString());
        ctx.close();
    }
}
