package com.phantomguard.controlplane.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless security: mutating administrative endpoints require the shared
 * admin API key (validated by {@link ApiKeyAuthFilter}); onboarding artifacts
 * (.mobileconfig downloads happen from Safari on the child device, with no
 * session) and the read APIs consumed by the dashboard remain open, scoped by
 * unguessable clientId/parentId UUIDs. Swap the API key filter for OAuth2 /
 * JWT when integrating a real identity provider.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ControlPlaneProperties properties;

    public SecurityConfig(ControlPlaneProperties properties) {
        this.properties = properties;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiKeyAuthFilter apiKeyAuthFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(apiKeyAuthFilter, BasicAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/policies/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/**").authenticated()
                        .requestMatchers("/api/**", "/ws/**", "/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(properties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
