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
RUN mkdir -p /music && chmod 755 /music
COPY --from=build /app/target/*.jar /app/app.jar
ENV JAVA_TOOL_OPTIONS="-Xms256m -Xmx768m"
EXPOSE 8081
HEALTHCHECK --interval=60s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health 2>/dev/null \
     || wget -qO- http://localhost:8081/ 2>/dev/null \
     || exit 0
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
