# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-support common-support
COPY discovery-service discovery-service
COPY api-gateway api-gateway
COPY patient-service patient-service
COPY appointment-service appointment-service
COPY billing-service billing-service
COPY notification-service notification-service
COPY integration-test integration-test
RUN mvn -pl appointment-service -am package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/appointment-service/target/*exec.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
