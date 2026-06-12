package com.phantomguard.controlplane;

import com.phantomguard.controlplane.config.ControlPlaneProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PhantomGuard Control Plane — the administrative and analytical brain.
 *
 * <p>Responsibilities: child profile provisioning ({@code .mobileconfig} /
 * DoT onboarding), dual-write policy synchronization (MongoDB + Redis +
 * cluster-wide invalidation broadcast), telemetry batch persistence into a
 * Mongo time-series collection, and the STOMP live feed consumed by the
 * parent dashboard.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ControlPlaneProperties.class)
public class ControlPlaneApplication {

    public static void main(String[] args) {
        SpringApplication.run(ControlPlaneApplication.class, args);
    }
}
