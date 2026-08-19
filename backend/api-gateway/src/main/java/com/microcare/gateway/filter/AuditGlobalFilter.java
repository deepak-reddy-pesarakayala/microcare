package com.microcare.gateway.filter;

import com.microcare.gateway.audit.AuditLogService;
import com.microcare.gateway.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Records an audit log entry for every non-public request that carries a valid
 * JWT, capturing who did what, when, from which IP and the outcome status.
 *
 * <p>Runs after {@link JwtAuthGlobalFilter} so the identity attributes are
 * already available, and writes the entry after the response completes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditGlobalFilter implements GlobalFilter, Ordered {

    private final AuditLogService auditLogService;
    private final JwtService jwtService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Only audit API requests (not actuator/public auth)
        if (path.startsWith("/auth/") || path.startsWith("/actuator/")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        Long userId = null;
        String username = null;
        String role = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parseToken(authHeader.substring(7));
                userId = claims.get("uid", Long.class);
                username = claims.getSubject();
                role = claims.get("role", String.class);
            } catch (JwtException | IllegalArgumentException ignored) {
                // invalid tokens are rejected by JwtAuthGlobalFilter; nothing to audit here
            }
        }

        // Capture into effectively-final locals so the completion lambda can reference them.
        final Long auditUserId = userId;
        final String auditUsername = username;
        final String auditRole = role;
        final String method = exchange.getRequest().getMethod() != null
                ? exchange.getRequest().getMethod().name() : "UNKNOWN";
        final String correlationId = (String) exchange.getAttributes()
                .getOrDefault(CorrelationIdGlobalFilter.CORRELATION_ATTRIBUTE, "");
        final String remoteIp = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : null;

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            Integer status = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value() : null;
            auditLogService.recordAsync(auditUserId, auditUsername, auditRole, method, path, status,
                    correlationId, remoteIp);
        }));
    }

    @Override
    public int getOrder() {
        return -50; // after JwtAuthGlobalFilter (-100), before LoggingFilter (-1)
    }
}
