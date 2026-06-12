package com.phantomguard.dataplane;

import com.phantomguard.dataplane.config.DataPlaneProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * PhantomGuard Data Plane — the encrypted DNS resolution engine.
 *
 * <p>Serves DNS-over-HTTPS on the embedded reactive Netty server
 * ({@code /dns-query/{clientId}}) and DNS-over-TLS on a dedicated Netty
 * listener (default port 853, SNI-routed). Policy decisions are made against
 * Redis-backed per-client blocklists with a local near-cache; telemetry is
 * streamed out-of-band so the resolution critical path never blocks.
 */
@SpringBootApplication
@EnableConfigurationProperties(DataPlaneProperties.class)
public class DataPlaneApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataPlaneApplication.class, args);
    }
}
