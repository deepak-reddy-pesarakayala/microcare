package com.microcare.gateway.auth;

import com.microcare.gateway.auth.dto.LoginRequest;
import com.microcare.gateway.auth.dto.LoginResponse;
import com.microcare.gateway.auth.dto.RegisterRequest;
import com.microcare.gateway.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Map;

/**
 * Handles login (password check against the {@code users} table) and
 * self-service patient registration for the API Gateway.
 *
 * <p>The gateway is reactive, so blocking JDBC lookups are isolated onto the
 * bounded-elastic scheduler to avoid blocking the Netty event loop.
 */
@Slf4j
@Service
public class AuthService {

    private static final String ROLE_PATIENT = "PATIENT";
    private static final String TOKEN_TYPE = "Bearer";

    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final WebClient patientServiceWebClient;

    public AuthService(JdbcTemplate jdbcTemplate,
                       BCryptPasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       @Value("${app.patient-service.base-url}") String patientServiceBaseUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.patientServiceWebClient = WebClient.builder()
                .baseUrl(patientServiceBaseUrl)
                .build();
    }

    /**
     * Validates credentials against the Users table and issues a signed JWT.
     */
    public Mono<LoginResponse> login(LoginRequest request) {
        return Mono.fromCallable(() -> doLogin(request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private LoginResponse doLogin(LoginRequest request) {
        UserRecord user = findByUsername(request.getUsername());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.passwordHash())) {
            log.warn("Authentication failed for username={}", request.getUsername());
            throw new ResponseStatusException(HttpStatusCode.valueOf(401),
                    "Invalid username or password");
        }

        String token = jwtService.createToken(
                user.username(), user.id(), user.role(), user.patientId(), user.doctorId());

        log.info("User logged in: userId={}, username={}, role={}", user.id(), user.username(), user.role());

        return LoginResponse.builder()
                .token(token)
                .tokenType(TOKEN_TYPE)
                .expiresIn(jwtService.expirationMs())
                .username(user.username())
                .role(user.role())
                .build();
    }

    /**
     * Creates a PATIENT-role account linked to an existing patient record.
     * The patient id is validated against patient-service first.
     */
    public Mono<Map<String, Object>> register(RegisterRequest request) {
        return Mono.fromCallable(() -> doRegister(request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Map<String, Object> doRegister(RegisterRequest request) {
        if (findByUsername(request.getUsername()) != null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "Username is already taken");
        }

        Boolean patientExists = patientServiceWebClient.get()
                .uri("/api/patients/{id}", request.getPatientId())
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return Mono.just(true);
                    }
                    if (response.statusCode().value() == 404) {
                        return Mono.just(false);
                    }
                    return Mono.just(true); // degraded — do not block registration
                })
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    log.warn("patient-service unreachable during registration for patientId={}: {}",
                            request.getPatientId(), e.getMessage());
                    return Mono.just(true);
                })
                .block();

        if (Boolean.FALSE.equals(patientExists)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "No patient record exists for patientId=" + request.getPatientId());
        }

        String hash = passwordEncoder.encode(request.getPassword());
        jdbcTemplate.update("""
                INSERT INTO users (username, password_hash, role, patient_id)
                VALUES (?, ?, ?, ?)
                """, request.getUsername(), hash, ROLE_PATIENT, request.getPatientId());

        log.info("Patient user registered: username={}, patientId={}",
                request.getUsername(), request.getPatientId());

        return Map.of("username", request.getUsername(),
                "role", ROLE_PATIENT,
                "patientId", request.getPatientId());
    }

    private UserRecord findByUsername(String username) {
        return jdbcTemplate.query("""
                        SELECT id, username, password_hash, role, patient_id, doctor_id
                        FROM users
                        WHERE username = ?
                        """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    return new UserRecord(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("role"),
                            (Long) rs.getObject("patient_id"),
                            (Long) rs.getObject("doctor_id"));
                }, username);
    }

    private record UserRecord(Long id, String username, String passwordHash,
                              String role, Long patientId, Long doctorId) {
    }
}
