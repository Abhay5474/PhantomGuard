package com.phantomguard.controlplane.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Authenticates administrative requests carrying the shared secret in the
 * {@code X-PG-Admin-Key} header. Constant-time comparison prevents timing
 * side channels on the key check.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-PG-Admin-Key";

    private final byte[] expectedKey;

    public ApiKeyAuthFilter(ControlPlaneProperties properties) {
        this.expectedKey = properties.getAdminApiKey().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String presented = request.getHeader(HEADER_NAME);
        if (presented != null && MessageDigest.isEqual(
                expectedKey, presented.getBytes(StandardCharsets.UTF_8))) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}
