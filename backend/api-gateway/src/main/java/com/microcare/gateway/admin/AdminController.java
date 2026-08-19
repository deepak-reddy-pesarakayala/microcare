package com.microcare.gateway.admin;

import com.microcare.gateway.auth.AuthService;
import com.microcare.gateway.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * ADMIN-only endpoints hosted directly on the gateway.
 *
 * <p><b>Security note:</b> {@code /api/admin/**} is intentionally <b>not</b> a
 * gateway route — requests are handled by this controller on the gateway itself.
 * Spring Cloud Gateway's global filters (including {@code JwtAuthGlobalFilter})
 * only run for requests that match a configured route, so this controller
 * validates the JWT and ADMIN role itself (the filter rule is kept as
 * defense-in-depth in case the path is ever routed).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_ADMIN = "ADMIN";

    private final AuthService authService;
    private final JwtService jwtService;

    @GetMapping("/doctors")
    public Mono<ResponseEntity<List<Map<String, Object>>>> listDoctors(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        requireAdmin(authorization);
        return Mono.fromCallable(() -> ResponseEntity.ok(authService.listDoctors()));
    }

    @PostMapping("/doctors/{id}/approve")
    public Mono<ResponseEntity<Map<String, Object>>> approveDoctor(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        requireAdmin(authorization);
        return Mono.fromCallable(() -> ResponseEntity.ok(authService.approveDoctor(id)));
    }

    @PostMapping("/doctors/{id}/reject")
    public Mono<ResponseEntity<Map<String, Object>>> rejectDoctor(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        requireAdmin(authorization);
        return Mono.fromCallable(() -> ResponseEntity.ok(authService.rejectDoctor(id)));
    }

    /**
     * Validates the caller's JWT and requires the ADMIN role. Throws 401 for a
     * missing/invalid token and 403 for a non-ADMIN caller.
     */
    private void requireAdmin(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Missing or malformed Authorization header");
        }
        Claims claims;
        try {
            claims = jwtService.parseToken(authorization.substring(BEARER_PREFIX.length()));
        } catch (JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid or expired token");
        }
        String role = claims.get("role", String.class);
        if (!ROLE_ADMIN.equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Admin privileges required");
        }
    }
}
