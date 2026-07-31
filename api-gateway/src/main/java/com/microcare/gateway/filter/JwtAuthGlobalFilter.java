package com.microcare.gateway.filter;

import com.microcare.gateway.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Validates the JWT on every request except {@code /auth/**} and {@code /actuator/**}
 * and enforces role-based access rules:
 *
 * <ul>
 *   <li><b>Appointment writes</b> ({@code POST/PUT/DELETE /api/appointments/**})
 *       are restricted to {@code ADMIN} or {@code DOCTOR} — 403 otherwise.</li>
 *   <li><b>Patients</b> ({@code PATIENT} role) may only read/update their own
 *       record ({@code GET/PUT /api/patients/{id}} where {@code {id}} matches the
 *       {@code pid} claim) — everything else on {@code /api/patients/**} is 403.</li>
 *   <li><b>Admin / Doctor</b> have full access.</li>
 * </ul>
 *
 * <p>Authenticated identity is forwarded to downstream services via
 * {@code X-User-Id}, {@code X-User-Role} and {@code X-User-Patient-Id} headers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    public static final String ATTR_USER_ID = "microcare.userId";
    public static final String ATTR_ROLE = "microcare.role";
    public static final String ATTR_PATIENT_ID = "microcare.patientId";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> APPOINTMENT_WRITE_ROLES = Set.of("ADMIN", "DOCTOR");
    private static final String ROLE_PATIENT = "PATIENT";

    private final JwtService jwtService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        // Public endpoints
        if (path.startsWith("/auth/") || path.startsWith("/actuator/")) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Missing or malformed Authorization header");
        }

        Claims claims;
        try {
            claims = jwtService.parseToken(authHeader.substring(BEARER_PREFIX.length()));
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed for path={}: {}", path, e.getMessage());
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }

        Long userId = claims.get("uid", Long.class);
        String role = claims.get("role", String.class);
        Long patientId = claims.get("pid", Long.class);

        // --- Role-based rules -------------------------------------------------
        boolean isAppointmentWrite = path.startsWith("/api/appointments/")
                && method != null
                && (HttpMethod.POST.equals(method)
                    || HttpMethod.PUT.equals(method)
                    || HttpMethod.DELETE.equals(method));
        if (isAppointmentWrite && !APPOINTMENT_WRITE_ROLES.contains(role)) {
            log.warn("Forbidden: role={} attempted appointment write to path={}", role, path);
            return reject(exchange, HttpStatus.FORBIDDEN,
                    "Appointment changes require ADMIN or DOCTOR role");
        }

        if (ROLE_PATIENT.equals(role)) {
            String forbidden = enforcePatientOwnRecord(path, method, patientId);
            if (forbidden != null) {
                log.warn("Forbidden: patient userId={} attempted {} to path={}", userId, method, path);
                return reject(exchange, HttpStatus.FORBIDDEN, forbidden);
            }
        }

        // --- Propagate identity downstream ------------------------------------
        exchange.getAttributes().put(ATTR_USER_ID, userId);
        exchange.getAttributes().put(ATTR_ROLE, role);
        exchange.getAttributes().put(ATTR_PATIENT_ID, patientId);

        ServerHttpRequest mutated = request.mutate()
                .header("X-User-Id", String.valueOf(userId))
                .header("X-User-Role", role)
                .header("X-User-Patient-Id", patientId != null ? String.valueOf(patientId) : "")
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /**
     * @return an error message if the PATIENT-role user is not allowed, else {@code null}
     */
    private String enforcePatientOwnRecord(String path, HttpMethod method, Long patientId) {
        // Spring 6's HttpMethod is a class (not an enum) — use equals() comparisons.
        boolean isOwnRecord = (HttpMethod.GET.equals(method) || HttpMethod.PUT.equals(method))
                && path.startsWith("/api/patients/")
                && matchesPatientId(path, patientId);
        return isOwnRecord ? null : "Patients may only access their own record";
    }

    private boolean matchesPatientId(String path, Long patientId) {
        String id = extractPatientIdFromPath(path);
        return id != null && patientId != null && id.equals(String.valueOf(patientId));
    }

    private String extractPatientIdFromPath(String path) {
        // /api/patients/{id}
        if (!path.startsWith("/api/patients/")) {
            return null;
        }
        String[] segments = path.split("/");
        if (segments.length < 4) {
            return null;
        }
        return segments[3];
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"status\":" + status.value()
                + ",\"error\":\"" + status.getReasonPhrase()
                + "\",\"message\":\"" + message + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return -100; // after CorrelationIdGlobalFilter
    }
}
