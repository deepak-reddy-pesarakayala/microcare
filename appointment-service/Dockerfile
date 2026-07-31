# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-support common-support
COPY appointment-service appointment-service
RUN mvn -pl appointment-service -am package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/appointment-service/target/*exec.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
