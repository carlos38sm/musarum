# Mismo patrón que backend: multi-stage Maven + JRE 21.
# Emendatium hace trabajo pesado (FFT, scraping MusicBrainz/Discogs,
# fingerprint acústico), así que le damos más memoria y GC paralelo.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
# Binarios externos:
#   - ffmpeg: usado por el enricher para decodificar audio al analizar BPM/key.
#   - libchromaprint-tools: aporta `fpcalc`, imprescindible para computar la
#     huella AcoustID → MBID → MusicBrainz → metadatos ricos. Sin esto el
#     enriquecimiento no identifica canciones y no rellena BPM/key/replaygain.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg libchromaprint-tools \
    && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /music && chmod 755 /music
COPY --from=build /app/target/*.jar /app/app.jar
ENV JAVA_TOOL_OPTIONS="-Xms256m -Xmx768m"
# Rutas por defecto de los binarios dentro de la imagen Linux. En Windows
# (dev local) se sobreescriben con las de application.properties.
ENV ENRICHER_BINARIES_FFMPEG=/usr/bin/ffmpeg \
    ENRICHER_BINARIES_FPCALC=/usr/bin/fpcalc
EXPOSE 8081
HEALTHCHECK --interval=60s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health 2>/dev/null \
     || wget -qO- http://localhost:8081/ 2>/dev/null \
     || exit 0
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
