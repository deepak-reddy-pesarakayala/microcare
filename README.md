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
| Service            | Port  | Tech Stack                    |
|--------------------|-------|-------------------------------|
| discovery-service  | 8761  | Eureka Server                 |
| api-gateway        | 8080  | Spring Cloud Gateway          |
| patient-service    | 8081  | Spring Web, JPA, MySQL        |

---

## Prerequisites

- **Java 21** (JDK 21+)
- **Apache Maven 3.9+**
- **Docker Desktop** (with Docker Compose v2)

---

## Quick Start

### 1. Start Infrastructure (MySQL, Redis, RabbitMQ)

```bash
docker compose up -d
```

Verify all services are healthy:
```bash
docker compose ps
```

### 2. Build all modules

```bash
mvn clean package -DskipTests
```

### 3. Start Services (in separate terminals)

**Terminal 1** — Discovery Service (Eureka):
```bash
cd discovery-service
mvn spring-boot:run
```

**Terminal 2** — API Gateway:
```bash
cd api-gateway
mvn spring-boot:run
```

**Terminal 3** — Patient Service:
```bash
cd patient-service
mvn spring-boot:run
```

> **Tip:** You can also run each service directly:
> ```bash
> java -jar discovery-service/target/discovery-service-1.0.0.jar
> java -jar api-gateway/target/api-gateway-1.0.0.jar
> java -jar patient-service/target/patient-service-1.0.0.jar
> ```

---

## API Endpoints

### Patient Service (via API Gateway)

All patient requests go through the **API Gateway** at `http://localhost:8080`.

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
├── docker-compose.yml               # Infrastructure services
├── README.md
├── discovery-service/               # Eureka Service Registry
│   ├── pom.xml
│   └── src/main/java/com/microcare/discovery/
│       ├── DiscoveryServiceApplication.java
│       └── resources/
│           └── application.yml
├── api-gateway/                     # Spring Cloud Gateway
│   ├── pom.xml
│   └── src/main/java/com/microcare/gateway/
│       ├── ApiGatewayApplication.java
│       ├── config/
│       │   └── GatewayConfig.java
│       ├── filter/
│       │   └── LoggingFilter.java
│       └── resources/
│           └── application.yml
└── patient-service/                 # Patient REST API
    ├── pom.xml
    └── src/main/java/com/microcare/patient/
        ├── PatientServiceApplication.java
        ├── controller/
        │   └── PatientController.java
        ├── service/
        │   └── PatientService.java
        ├── repository/
        │   └── PatientRepository.java
        ├── entity/
        │   └── Patient.java
        ├── dto/
        │   ├── PatientRequest.java
        │   ├── PatientResponse.java
        │   └── ErrorResponse.java
        └── exception/
            ├── ResourceNotFoundException.java
            └── GlobalExceptionHandler.java
```

---

## Stopping Everything

```bash
# Stop Docker containers
docker compose down

# Stop Docker containers and remove volumes (⚠️ destroys data)
docker compose down -v
```

## License

MIT
