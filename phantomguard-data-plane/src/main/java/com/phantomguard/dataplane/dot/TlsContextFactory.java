package com.phantomguard.dataplane.dot;

import com.phantomguard.dataplane.config.DataPlaneProperties;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.net.ssl.SSLException;
import java.io.File;
import java.security.cert.CertificateException;

/**
 * Builds the server {@link SslContext} for the DoT listener.
 *
 * <p>In production a wildcard certificate covering
 * {@code *.dns.yourdomain.com} (issued e.g. via Let's Encrypt DNS-01) must be
 * supplied, because every client connects to its own
 * {@code {clientId}.dns.yourdomain.com} hostname. When no certificate is
 * configured a self-signed one is generated so local development works out of
 * the box.
 */
@Component
public class TlsContextFactory {

    private static final Logger log = LoggerFactory.getLogger(TlsContextFactory.class);

    private final DataPlaneProperties properties;

    public TlsContextFactory(DataPlaneProperties properties) {
        this.properties = properties;
    }

    public SslContext createServerContext() {
        try {
            if (StringUtils.hasText(properties.getTlsCertChainPath())
                    && StringUtils.hasText(properties.getTlsPrivateKeyPath())) {
                File chain = new File(properties.getTlsCertChainPath());
                File key = new File(properties.getTlsPrivateKeyPath());
                if (!chain.isFile() || !key.isFile()) {
                    throw new IllegalStateException("Configured TLS material not found: chain="
                            + chain.getAbsolutePath() + ", key=" + key.getAbsolutePath());
                }
                log.info("DoT TLS: using configured certificate {}", chain.getAbsolutePath());
                return SslContextBuilder.forServer(chain, key).build();
            }
            SelfSignedCertificate ssc = new SelfSignedCertificate("*." + properties.getDnsDomainSuffix());
            log.warn("DoT TLS: no certificate configured — generated SELF-SIGNED cert for *.{} "
                    + "(development only; Android Private DNS will reject it)", properties.getDnsDomainSuffix());
            return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        } catch (SSLException | CertificateException e) {
            throw new IllegalStateException("Failed to initialise DoT TLS context", e);
        }
    }
}
