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
 *   <li>security rules are enforced (patients may only read their own record,
 *       only book/reschedule/cancel their own appointments, never create slots)</li>
 *   <li>a doctor creates slots and books an appointment (outbox → RabbitMQ)</li>
 *   <li>the patient self-books their own appointment through the gateway</li>
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
    private static long adminPatientId;
    private static long patientId;
    private static String patientToken;
    private static long appointmentId;
    private static long patientAppointmentId;

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
            bookAppointments();
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
                "fullName", "Rita Patel",
                "dateOfBirth", "1988-07-21",
                "gender", "Female",
                "email", "rita.patel@example.com",
                "bloodGroup", "A-"), adminToken);
        adminPatientId = body.get("id").asLong();
        assertTrue(adminPatientId > 0, "admin-created patient record should be created");
    }

    private static void registerAndLoginPatient() throws Exception {
        // Self-service registration: the profile is used to create the patient
        // record automatically — no pre-existing patient ID is required.
        JsonNode registerBody = postJson("/auth/register", Map.of(
                "username", "jane",
                "password", "password",
                "fullName", "Jane Doe",
                "email", "jane.doe@example.com",
                "gender", "Female",
                "bloodGroup", "O+"), null);
        patientId = registerBody.get("patientId").asLong();
        assertTrue(patientId > 0, "self-registration should create a patient record");
        assertEquals("PATIENT", registerBody.get("role").asText());

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
        // ...may browse available doctor slots (needed to self-book)...
        assertStatus("patient browses available slots",
                getSpec("/api/appointments/slots", patientToken), HttpStatus.OK);
        // ...and may list only their own appointments.
        assertStatus("patient lists own appointments",
                getSpec("/api/appointments?patientId=" + patientId, patientToken), HttpStatus.OK);
        // ...but cannot book on behalf of another patient.
        assertStatus("patient blocked booking for another patient",
                postSpec("/api/appointments/book", Map.of(
                        "patientId", 999999L, "doctorId", DOCTOR_ID, "slotId", 1L), patientToken),
                HttpStatus.FORBIDDEN);
        // ...and cannot create doctor slots (ADMIN/DOCTOR only).
        assertStatus("patient blocked from creating slots",
                postSpec("/api/appointments/slots", Map.of(
                        "doctorId", DOCTOR_ID,
                        "slotDate", LocalDate.now().plusDays(1).toString(),
                        "startTime", "09:00:00",
                        "endTime", "09:30:00"), patientToken),
                HttpStatus.FORBIDDEN);
        // Unauthenticated requests are rejected with 401.
        assertStatus("unauthenticated request rejected",
                getSpec("/api/patients", null), HttpStatus.UNAUTHORIZED);
    }

    private static void bookAppointments() throws Exception {
        String doctorToken = postJson("/auth/login",
                Map.of("username", "doctor", "password", PASSWORD), null)
                .get("token").asText();

        // Staff (DOCTOR) creates two AVAILABLE slots via the slot-management endpoint.
        long slot1 = postJson("/api/appointments/slots", Map.of(
                "doctorId", DOCTOR_ID,
                "slotDate", LocalDate.now().plusDays(1).toString(),
                "startTime", "09:00:00",
                "endTime", "09:30:00"), doctorToken).get("id").asLong();
        long slot2 = postJson("/api/appointments/slots", Map.of(
                "doctorId", DOCTOR_ID,
                "slotDate", LocalDate.now().plusDays(2).toString(),
                "startTime", "10:00:00",
                "endTime", "10:30:00"), doctorToken).get("id").asLong();

        // Staff books on behalf of a patient (outbox → RabbitMQ → invoice).
        JsonNode doctorBooking = CLIENT.post()
                .uri("/api/appointments/book")
                .header("Authorization", "Bearer " + doctorToken)
                .header("X-Correlation-Id", CORRELATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("patientId", patientId, "doctorId", DOCTOR_ID, "slotId", slot1))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        assertNotNull(doctorBooking, "doctor booking response should not be empty");
        appointmentId = doctorBooking.get("id").asLong();
        assertTrue(appointmentId > 0, "appointment should be created");

        // The PATIENT self-books their own appointment through the gateway.
        JsonNode patientBooking = CLIENT.post()
                .uri("/api/appointments/book")
                .header("Authorization", "Bearer " + patientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("patientId", patientId, "doctorId", DOCTOR_ID, "slotId", slot2))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        assertNotNull(patientBooking, "patient booking response should not be empty");
        patientAppointmentId = patientBooking.get("id").asLong();
        assertTrue(patientAppointmentId > 0, "patient self-booking should create an appointment");

        // Bookings start PENDING — the doctor must accept them before an
        // invoice is created via the outbox.
        assertEquals("PENDING", doctorBooking.get("status").asText());
        assertEquals("PENDING", patientBooking.get("status").asText());

        // The doctor accepts both bookings (own-appointment enforcement is on).
        JsonNode accepted = CLIENT.post()
                .uri("/api/appointments/" + appointmentId + "/accept")
                .header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        assertNotNull(accepted, "accept response should not be empty");
        assertEquals("CONFIRMED", accepted.get("status").asText());

        JsonNode accepted2 = CLIENT.post()
                .uri("/api/appointments/" + patientAppointmentId + "/accept")
                .header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        assertNotNull(accepted2, "accept response should not be empty");
        assertEquals("CONFIRMED", accepted2.get("status").asText());
    }

    private static void verifyInvoiceCreatedViaOutbox() {
        waitUntil("invoice creation via outbox",
                () -> db.count("microcare_billing",
                        "SELECT COUNT(*) FROM invoices WHERE appointment_id = ?", appointmentId) == 1
                        && db.count("microcare_billing",
                        "SELECT COUNT(*) FROM invoices WHERE appointment_id = ?", patientAppointmentId) == 1,
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
