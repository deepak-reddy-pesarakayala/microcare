package com.microcare.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcare.appointment.AppointmentServiceApplication;
import com.microcare.billing.BillingServiceApplication;
import com.microcare.gateway.ApiGatewayApplication;
import com.microcare.notification.NotificationServiceApplication;
import com.microcare.patient.PatientServiceApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full-system integration test (Testcontainers: MySQL + Redis + RabbitMQ).
 *
 * <p>Boots all five MicroCare services in-process (Eureka disabled, direct routing)
 * and verifies the end-to-end flow:
 * <ol>
 *   <li>admin logs in (JWT issued against the seeded Users table)</li>
 *   <li>admin registers a patient record</li>
 *   <li>the patient self-registers a user account and logs in</li>
 *   <li>security rules are enforced (patients may not book, may only read their own record)</li>
 *   <li>a doctor books an appointment (outbox → RabbitMQ)</li>
 *   <li>the billing-service creates an invoice</li>
 *   <li>the notification-service records a notification log entry</li>
 *   <li>the gateway records an audit log entry — all carrying the same correlation id</li>
 * </ol>
 *
 * <p>Run with {@code mvn verify -pl integration-test} (skipped automatically when
 * Docker is unavailable).
 *
 * <p><b>Config note:</b> every service jar ships a root {@code application.yml}, so
 * when a context boots, Spring merges all five — the per-service {@code it-*} profile
 * files re-specify every conflicting key (port, datasource, rabbitmq, eureka, discovery)
 * so the effective configuration is correct per service.
 */
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MicroCareSystemIT {

    private static final String GATEWAY_URL = "http://localhost:18080";
    private static final String PASSWORD = "password";
    private static final long DOCTOR_ID = 100L;
    private static final String CORRELATION_ID = "it-correlation-42";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("microcare_patients");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3.12-management-alpine")
            .withUser("microcare", "microcare_pass");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final WebClient CLIENT = WebClient.builder()
            .baseUrl(GATEWAY_URL)
            .build();

    private static DbHelper db;
    private static final List<ConfigurableApplicationContext> CONTEXTS = new java.util.ArrayList<>();

    private static String adminToken;
    private static long patientId;
    private static String patientToken;
    private static long appointmentId;

    @BeforeAll
    static void startSystem() {
        // Container addresses must be resolvable before any Spring context starts.
        System.setProperty("IT_MYSQL_HOST", MYSQL.getHost());
        System.setProperty("IT_MYSQL_PORT", String.valueOf(MYSQL.getMappedPort(3306)));
        System.setProperty("IT_REDIS_HOST", REDIS.getHost());
        System.setProperty("IT_REDIS_PORT", String.valueOf(REDIS.getMappedPort(6379)));
        System.setProperty("IT_RABBIT_HOST", RABBITMQ.getHost());
        System.setProperty("IT_RABBIT_PORT", String.valueOf(RABBITMQ.getMappedPort(5672)));

        db = new DbHelper(MYSQL.getHost(), MYSQL.getMappedPort(3306), "root", "test");
        db.runInitScripts();

        // Boot services in dependency order (gateway last — its routes point at the others).
        boot(PatientServiceApplication.class, "it-patient");
        boot(AppointmentServiceApplication.class, "it-appointment");
        boot(BillingServiceApplication.class, "it-billing");
        boot(NotificationServiceApplication.class, "it-notification");
        boot(ApiGatewayApplication.class, "it-gateway");

        // Wait for the gateway to be fully up.
        waitUntil("gateway health check",
                () -> getSpec("/actuator/health", null)
                        .exchangeToMono(reactor.core.publisher.Mono::just)
                        .blockOptional()
                        .map(r -> r.statusCode().is2xxSuccessful())
                        .orElse(false),
                Duration.ofSeconds(90));
    }

    @AfterAll
    static void stopSystem() {
        for (int i = CONTEXTS.size() - 1; i >= 0; i--) {
            try {
                CONTEXTS.get(i).close();
            } catch (Exception ignored) {
                // best effort
            }
        }
        CONTEXTS.clear();
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    void fullSystemFlow_WithinTimeout() {
        assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            loginAsAdmin();
            registerPatientRecord();
            registerAndLoginPatient();
            assertSecurityRules();
            bookAppointmentAsDoctor();
            verifyInvoiceCreatedViaOutbox();
            verifyNotificationLogEntry();
            verifyAuditLogEntry();
        });
    }

    // ---------------------------------------------------------------
    // Scenario steps
    // ---------------------------------------------------------------

    private static void loginAsAdmin() throws Exception {
        JsonNode body = postJson("/auth/login", Map.of("username", "admin", "password", PASSWORD), null);
        adminToken = body.get("token").asText();
        assertNotNull(adminToken, "admin JWT should be issued");
        assertEquals("ADMIN", body.get("role").asText());
    }

    private static void registerPatientRecord() throws Exception {
        JsonNode body = postJson("/api/patients", Map.of(
                "fullName", "Jane Doe",
                "dateOfBirth", "1992-04-11",
                "gender", "Female",
                "email", "jane.doe@example.com",
                "bloodGroup", "O+"), adminToken);
        patientId = body.get("id").asLong();
        assertTrue(patientId > 0, "patient record should be created");
    }

    private static void registerAndLoginPatient() throws Exception {
        postJson("/auth/register", Map.of(
                "username", "jane",
                "password", "password",
                "patientId", patientId), null);

        JsonNode body = postJson("/auth/login", Map.of("username", "jane", "password", PASSWORD), null);
        patientToken = body.get("token").asText();
        assertEquals("PATIENT", body.get("role").asText());
    }

    private static void assertSecurityRules() {
        // PATIENT may read their own record...
        assertStatus("patient reads own record",
                getSpec("/api/patients/" + patientId, patientToken), HttpStatus.OK);
        // ...but not someone else's.
        assertStatus("patient blocked from another record",
                getSpec("/api/patients/999999", patientToken), HttpStatus.FORBIDDEN);
        // ...and cannot book appointments (ADMIN/DOCTOR only).
        assertStatus("patient blocked from booking",
                postSpec("/api/appointments/book", Map.of(
                        "patientId", patientId, "doctorId", DOCTOR_ID, "slotId", 1L), patientToken),
                HttpStatus.FORBIDDEN);
        // Unauthenticated requests are rejected with 401.
        assertStatus("unauthenticated request rejected",
                getSpec("/api/patients", null), HttpStatus.UNAUTHORIZED);
    }

    private static void bookAppointmentAsDoctor() throws Exception {
        String doctorToken = postJson("/auth/login",
                Map.of("username", "doctor", "password", PASSWORD), null)
                .get("token").asText();

        // Seed an AVAILABLE slot for the doctor (no slot-management endpoint exists yet).
        db.execute("microcare_appointments", """
                INSERT INTO doctor_slots (doctor_id, slot_date, start_time, end_time, status, version, created_at, updated_at)
                VALUES (?, ?, '09:00:00', '09:30:00', 'AVAILABLE', 0, NOW(), NOW())
                """, DOCTOR_ID, LocalDate.now().plusDays(1));
        List<Map<String, Object>> slotRows = db.query("microcare_appointments",
                "SELECT id FROM doctor_slots WHERE doctor_id = ? LIMIT 1", DOCTOR_ID);
        long slotId = ((Number) slotRows.get(0).get("id")).longValue();

        JsonNode body = CLIENT.post()
                .uri("/api/appointments/book")
                .header("Authorization", "Bearer " + doctorToken)
                .header("X-Correlation-Id", CORRELATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("patientId", patientId, "doctorId", DOCTOR_ID, "slotId", slotId))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        assertNotNull(body, "booking response should not be empty");
        appointmentId = body.get("id").asLong();
        assertTrue(appointmentId > 0, "appointment should be created");
    }

    private static void verifyInvoiceCreatedViaOutbox() {
        waitUntil("invoice creation via outbox",
                () -> db.count("microcare_billing",
                        "SELECT COUNT(*) FROM invoices WHERE appointment_id = ?", appointmentId) == 1,
                Duration.ofSeconds(60));
    }

    private static void verifyNotificationLogEntry() {
        waitUntil("notification log entry",
                () -> {
                    List<Map<String, Object>> rows = db.query("microcare_notifications",
                            "SELECT correlation_id FROM notification_logs WHERE appointment_id = ?",
                            appointmentId);
                    return rows.size() == 1
                            && CORRELATION_ID.equals(rows.get(0).get("correlation_id"));
                },
                Duration.ofSeconds(60));
    }

    private static void verifyAuditLogEntry() {
        waitUntil("audit log entry",
                () -> {
                    List<Map<String, Object>> rows = db.query("microcare_auth",
                            "SELECT correlation_id, status_code, username FROM audit_logs WHERE path = '/api/appointments/book'");
                    return rows.stream().anyMatch(r -> CORRELATION_ID.equals(r.get("correlation_id"))
                            && Integer.valueOf(201).equals(toInteger(r.get("status_code")))
                            && "doctor".equals(r.get("username")));
                },
                Duration.ofSeconds(30));
    }

    // ---------------------------------------------------------------
    // Boot + HTTP helpers
    // ---------------------------------------------------------------

    private static void boot(Class<?> app, String profile) {
        SpringApplication springApplication = new SpringApplication(app);
        springApplication.setAdditionalProfiles(profile);
        springApplication.setLogStartupInfo(false);
        CONTEXTS.add(springApplication.run());
    }

    private static JsonNode postJson(String path, Map<String, Object> body, String token) {
        var request = CLIENT.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return request.bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    private static WebClient.RequestHeadersSpec<?> getSpec(String path, String token) {
        WebClient.RequestHeadersSpec<?> request = CLIENT.get().uri(path);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return request;
    }

    private static WebClient.RequestHeadersSpec<?> postSpec(String path, Map<String, Object> body, String token) {
        WebClient.RequestHeadersSpec<?> request = CLIENT.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return request;
    }

    private static void assertStatus(String label, WebClient.RequestHeadersSpec<?> spec, HttpStatus expected) {
        // exchangeToMono automatically releases the response body once the returned Mono completes.
        org.springframework.web.reactive.function.client.ClientResponse response =
                spec.exchangeToMono(reactor.core.publisher.Mono::just).block();
        assertNotNull(response, label + ": no response");
        assertEquals(expected.value(), response.statusCode().value(),
                label + ": expected " + expected.value() + " but got " + response.statusCode());
    }

    private static void waitUntil(String label, BooleanSupplier condition, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                if (condition.getAsBoolean()) {
                    return;
                }
            } catch (Exception e) {
                // retry
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for " + label, e);
            }
        }
        throw new AssertionError("Timed out waiting for " + label + " (timeout " + timeout + ")");
    }

    private static Integer toInteger(Object value) {
        return value instanceof Number n ? n.intValue() : Integer.valueOf(String.valueOf(value));
    }
}
