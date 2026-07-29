# ── Stage 1: build ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# ── Stage 2: runtime (standard — no OCR) ───────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S easywork && adduser -S easywork -G easywork
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown easywork:easywork app.jar
USER easywork
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

# ── Stage 3: runtime with OCR (ingest profile) ─────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS runtime-ocr
RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr tesseract-ocr-fra tesseract-ocr-eng \
  && rm -rf /var/lib/apt/lists/*
RUN groupadd -r easywork && useradd -r -g easywork easywork
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown easywork:easywork app.jar
USER easywork
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
