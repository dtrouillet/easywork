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
# Fixed UID/GID 1000 — must match the Helm chart's podSecurityContext
# (runAsUser/runAsGroup/fsGroup: 1000); a plain `-S` system user gets
# whatever UID is next available (typically <1000) and silently mismatches.
RUN addgroup -g 1000 -S easywork && adduser -u 1000 -S easywork -G easywork
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown easywork:easywork app.jar
USER easywork
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

# ── Stage 3: build Leptonica from source ────────────────────────────────────
# Ubuntu Jammy/Noble's tesseract-ocr package links against Leptonica 1.82.0,
# but tess4j 5.20.0's native binding (lept4j 1.24.0) calls
# pixAddMultipleBlackWhiteBorders(), only present from Leptonica 1.87.0 —
# lept4j bulk-registers its whole JNA function table at class-init time, so
# this single missing symbol breaks *all* OCR calls with UnsatisfiedLinkError.
# No Ubuntu release ships a new-enough Leptonica, so it's built from the
# official upstream source (checksum-pinned) into its own unversioned
# libleptonica.so — installed alongside (not replacing) the apt-provided
# liblept.so.5 that libtesseract.so.4 itself is linked against, so both the
# tesseract binary and tess4j's direct JNA calls keep working.
FROM ubuntu:22.04 AS lept-build
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential wget ca-certificates \
    libpng-dev libjpeg-turbo8-dev libtiff-dev zlib1g-dev libwebp-dev libopenjp2-7-dev libgif-dev \
  && rm -rf /var/lib/apt/lists/*
ARG LEPTONICA_VERSION=1.87.0
ARG LEPTONICA_SHA256=c73363397f96eb1295602bf44d708a994ad42046c791bf03ea0505d829bdb6a7
WORKDIR /src
RUN wget -q "https://github.com/DanBloomberg/leptonica/releases/download/${LEPTONICA_VERSION}/leptonica-${LEPTONICA_VERSION}.tar.gz" \
  && echo "${LEPTONICA_SHA256}  leptonica-${LEPTONICA_VERSION}.tar.gz" | sha256sum -c - \
  && tar xzf "leptonica-${LEPTONICA_VERSION}.tar.gz"
WORKDIR /src/leptonica-1.87.0
RUN ./configure --prefix=/opt/leptonica && make -j"$(nproc)" && make install

# ── Stage 4: runtime with OCR (ingest profile) ──────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS runtime-ocr
RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr tesseract-ocr-fra tesseract-ocr-eng \
  && rm -rf /var/lib/apt/lists/*
# Ubuntu Jammy's tesseract-ocr package (tesseract 4.1.1) installs trained data
# under .../4.00/tessdata, not /usr/share/tessdata (the app's generic fallback
# default in application.yml) — without this, Tesseract's native init throws
# "Specified datapath does not exist" on every OCR call. Re-check this path if
# the base image's Ubuntu/tesseract-ocr version changes.
ENV TESSDATA_PATH=/usr/share/tesseract-ocr/4.00/tessdata
# apt's tesseract-ocr only ships versioned .so.N files (no -dev package
# installed), but JNA/tess4j loads unversioned names ("libtesseract.so",
# "libleptonica.so") — see lept-build above for why libleptonica.so comes
# from a source build instead of a symlink to the apt-provided liblept.so.5.
COPY --from=lept-build /opt/leptonica/lib/libleptonica.so /usr/local/lib/libleptonica.so
RUN ln -s "$(ldconfig -p | grep -oP '/\S*libtesseract\.so\.\S*' | head -1)" \
      "$(dirname "$(ldconfig -p | grep -oP '/\S*libtesseract\.so\.\S*' | head -1)")/libtesseract.so" \
  && ldconfig
# Fixed UID/GID 1000 — see the `runtime` stage above for why.
RUN groupadd -g 1000 easywork && useradd -u 1000 -g easywork easywork
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown easywork:easywork app.jar
USER easywork
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
