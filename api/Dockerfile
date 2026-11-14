# --- Build stage: compile and package the jar ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the POM first so the dependency layer is cached until pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# --- Runtime stage: JRE only, no build tools, non-root user ---
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S vigie && adduser -S vigie -G vigie
WORKDIR /app
COPY --from=build /build/target/vigie-api.jar app.jar
USER vigie
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
