# MicroCare

A cloud-native, microservices-based Hospital Management Platform built with **Spring Boot 3.x**, **Java 21**, and **Spring Cloud**.

## Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌──────────────────┐
│   Client /      │────▶│   API Gateway   │────▶│  Discovery       │
│   curl / Postman│     │   (port 8080)   │     │  Service (Eureka)│
└─────────────────┘     └────────┬────────┘     │  (port 8761)     │
                                 │               └──────────────────┘
                                 │                        │
                    ┌────────────┴────────────┐           │
                    ▼                         ▼           │
         ┌──────────────────┐     ┌──────────────────┐    │
         │ patient-service  │     │  Future Services │    │
         │   (port 8081)    │     │                  │    │
         └────────┬─────────┘     └──────────────────┘    │
                  │                                        │
                  ▼                                        │
         ┌──────────────────┐                              │
         │  MySQL 8         │◀─────────────────────────────┘
         │  (port 3306)     │     Eureka Registration
         └──────────────────┘
```

### Infrastructure Services (Docker)
| Service   | Port(s)           | Purpose                |
|-----------|-------------------|------------------------|
| MySQL 8   | `3306`            | Patient data store     |
| Redis     | `6379`            | Caching                |
| RabbitMQ  | `5672` / `15672`  | Async messaging / Mgmt |

### Platform Services (Spring Boot)
| Service              | Port  | Tech Stack                                       |
|----------------------|-------|--------------------------------------------------|
| discovery-service    | 8761  | Eureka Server                                    |
| api-gateway          | 8080  | Spring Cloud Gateway, Spring Security + JWT, Redis rate limiting |
| patient-service      | 8081  | Spring Web, JPA, MySQL                           |
| appointment-service  | 8082  | Spring Web, JPA, Redisson, RabbitMQ (outbox)     |
| billing-service      | 8083  | Spring Web, JPA, RabbitMQ (consumer)             |
| notification-service | 8084  | Spring Web, JPA, RabbitMQ (consumer)             |

---

## Prerequisites

- **Java 21** (JDK 21+)
- **Apache Maven 3.9+**
- **Docker Desktop** (with Docker Compose v2)

---

## Quick Start

### 1. Build & start everything (infrastructure + all services)

```bash
docker compose up --build
```

Services start in dependency order via healthcheck-based `depends_on`.
Verify everything is healthy:
```bash
docker compose ps
```

### 2. Run locally (services on the host, infra in Docker)

```bash
# Infrastructure only
docker compose up -d mysql redis rabbitmq

# Build all modules
mvn clean package -DskipTests

# Start services in separate terminals (Eureka first, gateway next)
cd discovery-service && mvn spring-boot:run
cd api-gateway && mvn spring-boot:run
cd patient-service && mvn spring-boot:run
cd appointment-service && mvn spring-boot:run
cd billing-service && mvn spring-boot:run
cd notification-service && mvn spring-boot:run
```

> **Tip:** Runnable jars use the `-exec` classifier, e.g.
> `java -jar api-gateway/target/api-gateway-1.0.0-exec.jar`

### 3. Seeded users

The auth database is seeded via `init-scripts/01-create-auth-db.sql`:

| Username | Password   | Role   | Notes                          |
|----------|------------|--------|--------------------------------|
| `admin`  | `password` | ADMIN  | full access                    |
| `doctor` | `password` | DOCTOR | doctor_id = 100                |

> ⚠️ Change the seeded passwords and `JWT_SECRET` before any real deployment.

---

## Security Model

* **Login** — `POST /auth/login` validates against the `users` table (BCrypt) and returns a signed **JWT** with `role`, `uid`, `pid`/`did` claims.
* **Self-registration** — `POST /auth/register` creates a PATIENT-role account linked to an existing patient record.
* **Request validation** — a global `JwtAuthGlobalFilter` validates the JWT signature/expiry on every request except `/auth/**` and `/actuator/**`:
  * Missing/invalid/expired token → `401`
  * Appointment **writes** (`POST/PUT/DELETE /api/appointments/**`) → `ADMIN` or `DOCTOR` only, else `403`
  * PATIENT-role users may only access **their own** record (`GET/PUT /api/patients/{id}` matching the `pid` claim), else `403`
* **Rate limiting** — Redis-backed `RequestRateLimiter`/`RedisRateLimiter` on every route, keyed by client IP (custom `KeyResolver`). Over-limit requests → `429`. Tune via `RATE_LIMIT_REPLENISH_RATE` / `RATE_LIMIT_BURST_CAPACITY`.
* **Correlation IDs** — the gateway generates `X-Correlation-Id`, propagates it to downstream services (HTTP + Feign headers) and into RabbitMQ message headers (via the outbox), and includes it in every log line through the MDC.
* **Audit log** — every authenticated request is recorded in the `audit_logs` table (user, role, method, path, status, correlation id, IP).

### Authentication

```bash
# Login (ADMIN)
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "password"}'

# Use the returned token on all other endpoints
curl http://localhost:8080/api/patients/1 \
  -H "Authorization: Bearer <token>"
```

## API Endpoints

### Patient Service (via API Gateway)

All patient requests go through the **API Gateway** at `http://localhost:8080` and **require a JWT** (`Authorization: Bearer <token>`).

#### Create a Patient
```bash
curl -X POST http://localhost:8080/api/patients \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "John Doe",
    "dateOfBirth": "1990-05-15",
    "gender": "Male",
    "contactNumber": "+1-555-123-4567",
    "email": "john.doe@example.com",
    "address": "123 Main St, Springfield, IL",
    "bloodGroup": "O+"
  }'
```

#### Get Patient by ID
```bash
curl http://localhost:8080/api/patients/1
```

#### Get All Patients
```bash
curl http://localhost:8080/api/patients
```

#### Update a Patient
```bash
curl -X PUT http://localhost:8080/api/patients/1 \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "John Smith",
    "dateOfBirth": "1990-05-15",
    "gender": "Male",
    "contactNumber": "+1-555-987-6543",
    "email": "john.smith@example.com",
    "address": "456 Oak Ave, Springfield, IL",
    "bloodGroup": "A+"
  }'
```

### Direct Access (for testing)

You can also hit the patient-service directly (bypassing the gateway):

```bash
curl http://localhost:8081/api/patients
```

### Health Checks

```bash
# Discovery Service
curl http://localhost:8761/actuator/health

# API Gateway
curl http://localhost:8080/actuator/health

# Patient Service (direct)
curl http://localhost:8081/actuator/health
```

### Eureka Dashboard

Open [http://localhost:8761](http://localhost:8761) in your browser to see the Eureka dashboard showing registered services.

### RabbitMQ Management UI

Open [http://localhost:15672](http://localhost:15672) — login with **microcare / microcare_pass**.

---

## Validation & Error Handling

The patient-service includes comprehensive request validation:

```bash
# Missing required fields → 400 with validation errors
curl -X POST http://localhost:8080/api/patients \
  -H "Content-Type: application/json" \
  -d '{}'

# Non-existent patient → 404 with error message
curl http://localhost:8080/api/patients/9999
```

Example error response:
```json
{
  "status": 400,
  "error": "Validation Failed",
  "message": "Input validation failed. See validationErrors for details.",
  "path": "/api/patients",
  "timestamp": "2026-07-29T12:00:00",
  "validationErrors": [
    {
      "field": "fullName",
      "message": "Full name is required"
    }
  ]
}
```

---

## Project Structure

```
microcare/
├── pom.xml                          # Root Maven POM (multi-module)
├── docker-compose.yml               # Infrastructure + all platform services
├── init-scripts/                    # MySQL first-boot scripts (auth/users, databases)
├── README.md
├── discovery-service/               # Eureka Service Registry
├── common-support/                  # Shared correlation-ID utilities
├── api-gateway/                     # Spring Cloud Gateway + Spring Security/JWT + rate limiting
├── patient-service/                 # Patient REST API
├── appointment-service/             # Appointment booking (Redisson lock + outbox)
├── billing-service/                 # Invoice creation (RabbitMQ consumer)
├── notification-service/            # Notification log entries (RabbitMQ consumer)
└── integration-test/                # Testcontainers full-system test (mvn verify)
```

## Running the System Integration Test

Requires Docker Desktop (started before running). The test boots every service
against Testcontainers MySQL/Redis/RabbitMQ and verifies the full flow:
register patient → login → security rules → doctor books appointment → invoice
via outbox → notification log → audit log — all within a timeout.

```bash
mvn verify -pl integration-test
```

---

## Stopping Everything

```bash
# Stop Docker containers
docker compose down

# Stop Docker containers and remove volumes (⚠️ destroys data)
docker compose down -v
```

## Remaining Gaps / TODOs (before frontend development)

1. **Role-scoped data access** — enforce *row-level* authorization inside services (not just the gateway): patients should only see their own appointments/invoices, doctors only their own slots/schedule.
2. **JWT secret management** — move `JWT_SECRET` to a secrets manager / vault; add token refresh flow and logout/revocation (e.g., Redis denylist).
3. **HTTPS/TLS termination** and `X-Forwarded-For` trust config at the edge for real deployments.
4. **Doctor slot management** — there is no endpoint to create/manage `doctor_slots` yet (test seeds them directly via SQL).
5. **Notification delivery** — the notification-service only *logs* entries; add actual email/SMS dispatch (e.g., Spring Mail, SendGrid).
6. **Password policy & account lifecycle** — lockouts, password reset, admin user management UI/API.
7. **Audit log retention/purging** and a query/export endpoint for compliance.
8. **Metrics & dashboards** — Prometheus/Grafana on actuator metrics; distributed tracing (Micrometer Tracing + Zipkin) instead of correlation-ID-only.
9. **Test coverage** — add unit tests for the new gateway filters (JWT auth, rate limiting) and notification service.
10. **CI pipeline** — build, run `mvn verify` (including the system IT), and image scanning.

## License

MIT
