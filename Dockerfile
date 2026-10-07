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

# Collect the Playwright jars so the runtime stage can run its installer
RUN mvn -q dependency:copy-dependencies -DincludeGroupIds=com.microsoft.playwright -DoutputDirectory=/build/playwright-jars

# ==========================================
# Stage 2: Runtime stage
# ==========================================
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

# Install fontconfig and fonts required for PDFBox rendering & headless AWT graphics, curl for the container healthcheck,
# and ffmpeg (which provides ffprobe) for the trailer pipeline, which checks every finished video itself
RUN apt-get update && \
    apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core curl ffmpeg && \
    rm -rf /var/lib/apt/lists/*

# Chromium + its system libraries for the storybook PDF renderer
COPY --from=builder /build/playwright-jars /opt/playwright-jars
RUN java -cp "/opt/playwright-jars/*" com.microsoft.playwright.CLI install --with-deps chromium && \
    chmod -R a+rX /ms-playwright && \
    rm -rf /var/lib/apt/lists/*

# From here on the app must never try to download a browser at runtime
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

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
