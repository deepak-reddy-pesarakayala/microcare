# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY discovery-service discovery-service
RUN mvn -pl discovery-service -am package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/discovery-service/target/*exec.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
