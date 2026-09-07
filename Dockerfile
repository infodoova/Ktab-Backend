# ==========================================
# Stage 1: Build stage
# ==========================================
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and build production artifact
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==========================================
# Stage 2: Runtime stage
# ==========================================
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install fontconfig and fonts required for PDFBox rendering & headless AWT graphics
RUN apt-get update && \
    apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core && \
    rm -rf /var/lib/apt/lists/*

# Run as non-root user for security
RUN groupadd -r ktab && useradd -r -g ktab -m ktab
USER ktab:ktab

# Copy the built executable WAR from builder
COPY --from=builder --chown=ktab:ktab /build/target/*.war app.war

# Container and JVM runtime environment
ENV PORT=8080 \
    SPRING_PROFILES_ACTIVE=production \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC -Xss512k -Djava.awt.headless=true"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.war"]
