package com.microcare.gateway.auth;

import com.microcare.gateway.auth.dto.LoginRequest;
import com.microcare.gateway.auth.dto.LoginResponse;
import com.microcare.gateway.auth.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public authentication endpoints. These are exempt from the gateway's JWT
 * filter (see {@code JwtAuthGlobalFilter} which skips {@code /auth/**}).
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /auth/login — validates credentials and returns a signed JWT.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request)
                .map(ResponseEntity::ok)
                .block();
    }

    /**
     * POST /auth/register — self-service registration for PATIENT-role accounts,
     * linked to an existing patient record.
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request)
                .map(map -> ResponseEntity.status(HttpStatus.CREATED).body(map))
                .block();
    }
}
