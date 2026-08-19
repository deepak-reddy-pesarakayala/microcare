-- Create the auth database for the API Gateway (Users + audit log)
CREATE DATABASE IF NOT EXISTS microcare_auth;
GRANT ALL PRIVILEGES ON microcare_auth.* TO 'microcare_user'@'%';
FLUSH PRIVILEGES;

USE microcare_auth;

CREATE TABLE IF NOT EXISTS users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50)   NOT NULL UNIQUE,
    password_hash   VARCHAR(100)  NOT NULL,
    role            VARCHAR(20)   NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'APPROVED',
    full_name       VARCHAR(150)  NULL,
    email           VARCHAR(150)  NULL,
    specialization  VARCHAR(100)  NULL,
    license_number  VARCHAR(100)  NULL,
    -- Comma-separated list of diseases/conditions the doctor treats, used to
    -- classify doctors in the patient-facing directory (falls back to the
    -- specialization → diseases mapping in the gateway when empty).
    diseases        VARCHAR(255)  NULL,
    patient_id      BIGINT        NULL,
    doctor_id       BIGINT        NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT       NULL,
    username       VARCHAR(50)  NULL,
    role           VARCHAR(20)  NULL,
    method         VARCHAR(10)  NOT NULL,
    path           VARCHAR(500) NOT NULL,
    status_code    INT          NULL,
    correlation_id VARCHAR(64)  NULL,
    remote_ip      VARCHAR(45)  NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_created_at (created_at),
    INDEX idx_audit_correlation_id (correlation_id)
);

-- Seed users.
-- NOTE: password_hash below is the BCrypt hash of the literal password "password"
-- ($2a$10$vGzhg6d/sEkK8xPtGYpAfe9iesGgOycg25O9bPG1jK0g7bzG1aGva) — verified by
-- api-gateway AuthPasswordSeedTest. Change the passwords before any real deployment.
-- doctor_id=100 matches the seeded doctor slot used in the integration test.
-- status controls account access: APPROVED accounts can sign in; PENDING/REJECTED
-- doctor accounts are blocked at login until an ADMIN approves them.
INSERT INTO users (username, password_hash, role, status, full_name, email, specialization, diseases, doctor_id) VALUES
    ('admin',      '$2a$10$vGzhg6d/sEkK8xPtGYpAfe9iesGgOycg25O9bPG1jK0g7bzG1aGva', 'ADMIN',  'APPROVED', 'System Administrator',  'admin@microcare.local',           NULL,        NULL,                                        NULL),
    ('doctor',     '$2a$10$vGzhg6d/sEkK8xPtGYpAfe9iesGgOycg25O9bPG1jK0g7bzG1aGva', 'DOCTOR', 'APPROVED', 'Dr. Sarah Chen',        'sarah.chen@microcare.local',      'Cardiology', 'Heart disease,Hypertension,Arrhythmia',    100),
    ('dr.olivia',  '$2a$10$vGzhg6d/sEkK8xPtGYpAfe9iesGgOycg25O9bPG1jK0g7bzG1aGva', 'DOCTOR', 'PENDING',  'Dr. Olivia Bennett',    'olivia.bennett@microcare.local', 'Pediatrics', 'Child health,Immunization,Growth disorders', NULL)
ON DUPLICATE KEY UPDATE username = VALUES(username);
