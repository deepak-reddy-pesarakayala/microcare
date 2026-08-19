package com.microcare.gateway.admin;

import com.microcare.gateway.auth.AuthService;
import com.microcare.gateway.security.JwtService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Public doctor directory — lets any <b>authenticated</b> user (e.g. patients
 * on the dashboard) browse approved doctors and the services they provide
 * (specialization), then book an appointment with them.
 *
 * <p>Like {@link AdminController}, this endpoint is hosted directly on the
 * gateway (it is not a proxied route), so the JWT is validated here — any
 * valid token from any role is accepted.
 */
@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
public class DoctorDirectoryController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final JwtService jwtService;

    @GetMapping
    public Mono<ResponseEntity<List<Map<String, Object>>>> listDoctors(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        requireAuth(authorization);
        return Mono.fromCallable(() -> ResponseEntity.ok(authService.listApprovedDoctors()));
    }

    /**
     * Validates the caller's JWT. Throws 401 for a missing/invalid token. Any
     * authenticated role may browse the directory.
     */
    private void requireAuth(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Missing or malformed Authorization header");
        }
        try {
            jwtService.parseToken(authorization.substring(BEARER_PREFIX.length()));
        } catch (JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid or expired token");
        }
    }
}
