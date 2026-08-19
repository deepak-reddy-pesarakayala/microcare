package com.microcare.gateway.auth;

import com.microcare.gateway.auth.dto.LoginRequest;
import com.microcare.gateway.auth.dto.LoginResponse;
import com.microcare.gateway.auth.dto.RegisterDoctorRequest;
import com.microcare.gateway.auth.dto.RegisterRequest;
import com.microcare.gateway.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private static final String ROLE_DOCTOR = "DOCTOR";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String TOKEN_TYPE = "Bearer";

    /**
     * Classifies a doctor by the diseases they treat when no explicit
     * {@code diseases} list is stored on the account. Keys are matched
     * case-insensitively against the doctor's specialization.
     */
    private static final Map<String, List<String>> SPECIALIZATION_DISEASES = Map.of(
            "Cardiology", List.of("Heart disease", "Hypertension", "Arrhythmia"),
            "Pediatrics", List.of("Child health", "Immunization", "Growth disorders"),
            "Neurology", List.of("Migraine", "Epilepsy", "Stroke"),
            "Dermatology", List.of("Eczema", "Psoriasis", "Acne"),
            "ENT", List.of("Sinusitis", "Hearing loss", "Tonsillitis"),
            "Orthopedics", List.of("Fractures", "Arthritis", "Back pain"),
            "General", List.of("Fever", "Infections", "Preventive care"));

    private static final List<String> DEFAULT_DISEASES = List.of("General consultation");

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final WebClient patientServiceWebClient;

    public AuthService(JdbcTemplate jdbcTemplate,
                       PasswordEncoder passwordEncoder,
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

        // Doctor accounts are gated behind admin approval.
        if (ROLE_DOCTOR.equals(user.role())) {
            if (STATUS_PENDING.equals(user.status())) {
                log.info("Login blocked for pending doctor username={}", request.getUsername());
                throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                        "Your doctor account is awaiting admin approval.");
            }
            if (STATUS_REJECTED.equals(user.status())) {
                log.info("Login blocked for rejected doctor username={}", request.getUsername());
                throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                        "Your doctor account was rejected. Please contact the administrator.");
            }
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
     * Creates a PATIENT-role account. The user's profile details are used to
     * create their patient record in patient-service first (self-service
     * registration), then the account is linked to the returned patient id.
     */
    public Mono<Map<String, Object>> register(RegisterRequest request) {
        return Mono.fromCallable(() -> doRegister(request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Self-service registration for DOCTOR-role accounts. The account is created
     * in PENDING status and cannot sign in until an ADMIN approves it
     * ({@link #approveDoctor}).
     */
    public Mono<Map<String, Object>> registerDoctor(RegisterDoctorRequest request) {
        return Mono.fromCallable(() -> doRegisterDoctor(request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Map<String, Object> doRegisterDoctor(RegisterDoctorRequest request) {
        if (findByUsername(request.getUsername()) != null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "Username is already taken");
        }

        String hash = passwordEncoder.encode(request.getPassword());
        jdbcTemplate.update("""
                INSERT INTO users (username, password_hash, role, status,
                                   full_name, email, specialization, license_number, diseases)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, request.getUsername(), hash, ROLE_DOCTOR, STATUS_PENDING,
                request.getFullName(), request.getEmail(),
                request.getSpecialization(), request.getLicenseNumber(),
                request.getDiseases());

        log.info("Doctor user registered (pending approval): username={}, name={}",
                request.getUsername(), request.getFullName());

        return Map.of("username", request.getUsername(),
                "role", ROLE_DOCTOR,
                "status", STATUS_PENDING,
                "message", "Your doctor account is pending admin approval.");
    }

    /**
     * Lists every DOCTOR-role account (pending, approved and rejected) so an
     * ADMIN can review and approve/reject new doctor sign-ups.
     */
    public List<Map<String, Object>> listDoctors() {
        return jdbcTemplate.queryForList("""
                        SELECT id, username, status, full_name, email,
                               specialization, license_number, diseases, doctor_id,
                               DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s') AS created_at
                        FROM users
                        WHERE role = ?
                        ORDER BY created_at DESC, id DESC
                        """, ROLE_DOCTOR).stream()
                .map(row -> {
                    Map<String, Object> doctor = toDoctorMap(row);
                    doctor.put("status", row.get("status"));
                    putIfNotNull(doctor, "createdAt", row.get("created_at"));
                    return doctor;
                })
                .toList();
    }

    /**
     * Lists only APPROVED DOCTOR accounts that have a doctor id assigned, so any
     * authenticated user (e.g. patients on the dashboard) can browse the doctor
     * directory and book an appointment with them.
     */
    public List<Map<String, Object>> listApprovedDoctors() {
        return jdbcTemplate.queryForList("""
                        SELECT id, username, full_name, email,
                               specialization, license_number, diseases, doctor_id
                        FROM users
                        WHERE role = ? AND status = ? AND doctor_id IS NOT NULL
                        ORDER BY full_name, id
                        """, ROLE_DOCTOR, STATUS_APPROVED).stream()
                .map(this::toDoctorMap)
                .toList();
    }

    /**
     * Maps a {@code users} row to a doctor payload. {@code Map.of} rejects null
     * values, so only non-null fields are carried.
     */
    private Map<String, Object> toDoctorMap(Map<String, Object> row) {
        Map<String, Object> doctor = new HashMap<>();
        doctor.put("id", row.get("id"));
        doctor.put("username", row.get("username"));
        putIfNotNull(doctor, "fullName", row.get("full_name"));
        putIfNotNull(doctor, "email", row.get("email"));
        putIfNotNull(doctor, "specialization", row.get("specialization"));
        putIfNotNull(doctor, "licenseNumber", row.get("license_number"));
        putIfNotNull(doctor, "doctorId", row.get("doctor_id"));
        doctor.put("diseases", diseasesFor(row));
        return doctor;
    }

    /**
     * Resolves the list of diseases a doctor treats: the stored comma-separated
     * value when present, otherwise derived from the specialization.
     */
    private List<String> diseasesFor(Map<String, Object> row) {
        Object stored = row.get("diseases");
        if (stored != null) {
            String text = String.valueOf(stored).trim();
            if (!text.isBlank()) {
                return java.util.Arrays.stream(text.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .toList();
            }
        }
        String specialization = row.get("specialization") == null
                ? null
                : String.valueOf(row.get("specialization")).trim();
        if (specialization != null && !specialization.isBlank()) {
            return SPECIALIZATION_DISEASES.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(specialization))
                    .findFirst()
                    .map(Map.Entry::getValue)
                    .orElse(DEFAULT_DISEASES);
        }
        return DEFAULT_DISEASES;
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    /**
     * Approves a pending DOCTOR account: flips it to APPROVED and assigns a
     * numeric doctor id (the user's own PK) so appointment-service can create
     * slots and book appointments for this doctor.
     */
    public Map<String, Object> approveDoctor(Long userId) {
        return setDoctorStatus(userId, STATUS_APPROVED);
    }

    /**
     * Rejects a pending DOCTOR account, permanently blocking its login.
     */
    public Map<String, Object> rejectDoctor(Long userId) {
        return setDoctorStatus(userId, STATUS_REJECTED);
    }

    private Map<String, Object> setDoctorStatus(Long userId, String newStatus) {
        UserRecord user = findById(userId);
        if (user == null || !ROLE_DOCTOR.equals(user.role())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404),
                    "Doctor account not found");
        }
        if (STATUS_APPROVED.equals(newStatus)) {
            // doctor_id = the user's own PK: unique, stable and available immediately.
            jdbcTemplate.update("""
                    UPDATE users
                    SET status = ?, doctor_id = ?
                    WHERE id = ?
                    """, newStatus, userId, userId);
        } else {
            jdbcTemplate.update("""
                    UPDATE users
                    SET status = ?
                    WHERE id = ?
                    """, newStatus, userId);
        }
        log.info("Doctor account {}: id={}, username={}", newStatus, userId, user.username());
        return Map.of("id", userId, "username", user.username(), "status", newStatus);
    }

    private Map<String, Object> doRegister(RegisterRequest request) {
        if (findByUsername(request.getUsername()) != null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "Username is already taken");
        }

        Long patientId = createPatientRecord(request);

        String hash = passwordEncoder.encode(request.getPassword());
        jdbcTemplate.update("""
                INSERT INTO users (username, password_hash, role, patient_id)
                VALUES (?, ?, ?, ?)
                """, request.getUsername(), hash, ROLE_PATIENT, patientId);

        log.info("Patient user registered: username={}, patientId={}",
                request.getUsername(), patientId);

        return Map.of("username", request.getUsername(),
                "role", ROLE_PATIENT,
                "patientId", patientId);
    }

    /**
     * Creates a patient record in patient-service from the registration profile
     * and returns the generated patient id.
     */
    private Long createPatientRecord(RegisterRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", request.getFullName());
        body.put("email", request.getEmail());
        if (request.getDateOfBirth() != null) {
            body.put("dateOfBirth", request.getDateOfBirth().toString());
        }
        if (request.getGender() != null) {
            body.put("gender", request.getGender());
        }
        if (request.getContactNumber() != null) {
            body.put("contactNumber", request.getContactNumber());
        }
        if (request.getAddress() != null) {
            body.put("address", request.getAddress());
        }
        if (request.getBloodGroup() != null) {
            body.put("bloodGroup", request.getBloodGroup());
        }

        return patientServiceWebClient.post()
                .uri("/api/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(Map.class)
                                .flatMap(errorBody -> {
                                    Object validationErrors = errorBody.get("validationErrors");
                                    if (validationErrors instanceof List<?> errors && !errors.isEmpty()) {
                                        String detail = errors.stream()
                                                .filter(Map.class::isInstance)
                                                .map(Map.class::cast)
                                                .map(error -> String.valueOf(error.get("message")))
                                                .filter(message -> message != null && !"null".equals(message))
                                                .distinct()
                                                .limit(3)
                                                .collect(Collectors.joining("; "));
                                        if (!detail.isEmpty()) {
                                            return Mono.error(new ResponseStatusException(
                                                    response.statusCode(), detail));
                                        }
                                    }
                                    Object message = errorBody.get("message");
                                    return Mono.error(new ResponseStatusException(
                                            response.statusCode(),
                                            message != null ? String.valueOf(message)
                                                    : "Invalid patient details"));
                                }))
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        Mono.error(new ResponseStatusException(HttpStatusCode.valueOf(503),
                                "Could not create patient record. Please try again later.")))
                .bodyToMono(Map.class)
                .map(created -> {
                    Object id = created.get("id");
                    if (id instanceof Number number) {
                        return number.longValue();
                    }
                    throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                            "patient-service returned an invalid patient record");
                })
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    if (e instanceof ResponseStatusException statusException) {
                        return Mono.error(statusException);
                    }
                    log.warn("patient-service unreachable while creating patient record: {}",
                            e.getMessage());
                    return Mono.error(new ResponseStatusException(HttpStatusCode.valueOf(503),
                            "Could not create patient record. Please try again later."));
                })
                .block();
    }

    private UserRecord findByUsername(String username) {
        return jdbcTemplate.query("""
                        SELECT id, username, password_hash, role, status, patient_id, doctor_id
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
                            rs.getString("status"),
                            (Long) rs.getObject("patient_id"),
                            (Long) rs.getObject("doctor_id"));
                }, username);
    }

    private UserRecord findById(Long userId) {
        return jdbcTemplate.query("""
                        SELECT id, username, password_hash, role, status, patient_id, doctor_id
                        FROM users
                        WHERE id = ?
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
                            rs.getString("status"),
                            (Long) rs.getObject("patient_id"),
                            (Long) rs.getObject("doctor_id"));
                }, userId);
    }

    private record UserRecord(Long id, String username, String passwordHash,
                              String role, String status, Long patientId, Long doctorId) {
    }
}
