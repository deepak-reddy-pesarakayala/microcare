# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-support common-support
COPY notification-service notification-service
RUN mvn -pl notification-service -am package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/notification-service/target/*exec.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
