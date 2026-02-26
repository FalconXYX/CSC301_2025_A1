# Build stage
FROM maven:3.9.2-eclipse-temurin-17 AS builder
WORKDIR /build

# Copy pom and source
COPY pom.xml .
COPY src ./src

# Build application
RUN mvn clean package -DskipTests -q

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy built jar
COPY --from=builder /build/target/product-service-1.0.0.jar app.jar
COPY --from=builder /build/target/dependency-reduced-pom.xml ./dependency-reduced-pom.xml

# Expose port
EXPOSE 15000

# Run application
ENTRYPOINT ["java", "-jar", "app.jar", "/app/config.json"]
