package com.phantomguard.dataplane.dot;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SniCompletionEvent;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Captures the TLS Server Name Indication negotiated during the handshake and
 * derives the {@code clientId} from hostnames shaped like
 * {@code {clientId}.dns.yourdomain.com}. Connections presenting no SNI or a
 * hostname outside the configured suffix are closed immediately — the DoT
 * listener is not an open resolver.
 */
public class SniClientIdExtractor extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(SniClientIdExtractor.class);

    public static final AttributeKey<String> CLIENT_ID_ATTR = AttributeKey.valueOf("phantomguard.clientId");

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final String expectedSuffix;

    public SniClientIdExtractor(String dnsDomainSuffix) {
        this.expectedSuffix = "." + dnsDomainSuffix.toLowerCase(Locale.ROOT);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SniCompletionEvent sniEvent) {
            String hostname = sniEvent.hostname();
            String clientId = extractClientId(hostname);
            if (clientId == null) {
                log.info("Rejecting DoT connection from {} — unroutable SNI '{}'",
                        ctx.channel().remoteAddress(), hostname);
                ctx.close();
                return;
            }
            ctx.channel().attr(CLIENT_ID_ATTR).set(clientId);
        }
        super.userEventTriggered(ctx, evt);
    }

    private String extractClientId(String hostname) {
        if (hostname == null) {
            return null;
        }
        String normalized = hostname.toLowerCase(Locale.ROOT);
        if (!normalized.endsWith(expectedSuffix)) {
            return null;
        }
        String label = normalized.substring(0, normalized.length() - expectedSuffix.length());
        return UUID_PATTERN.matcher(label).matches() ? label : null;
    }
}
