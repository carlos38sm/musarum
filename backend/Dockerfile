# Multi-stage: build con Maven + Java 21 → runtime con JRE 21 slim.
# No usamos alpine porque jaudiotagger tira de dependencias nativas que
# en Alpine (musl) a veces dan sorpresas; eclipse-temurin oficial va fino.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Copiamos pom.xml y descargamos dependencias en una capa aparte para que
# cachee entre builds mientras el pom no cambie.
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
# Carpetas que el backend crea bajo MUSARUM_MUSIC_PATH (_covers, _lyrics,
# _videos, _profilepictures). Las monta el compose como volúmenes.
RUN mkdir -p /music && chmod 755 /music
# Copiamos el jar sin incluir el nombre exacto (varía con la versión del pom).
COPY --from=build /app/target/*.jar /app/app.jar
# JVM: heap generoso (1.5 GB) + G1GC concurrente. Antes iba con -Xmx384m
# + SerialGC "para portátil viejo", pero con 3000+ canciones en catálogo,
# Spring Boot full, Hibernate caches, OSHI, OkHttp y Flyway la JVM pasaba
# más tiempo en GC stop-the-world que respondiendo. Síntoma: 114% CPU en
# el proceso Java mientras Postgres y Emendatium estaban ociosos, y toda
# la app lenta. En un servidor con ≥3 GB libres esto respira a gusto; si
# quisieras bajarlo en una máquina más pequeña, override con
# JAVA_TOOL_OPTIONS en docker-compose.yml.
ENV JAVA_TOOL_OPTIONS="-Xms256m -Xmx1536m -XX:+UseG1GC"
EXPOSE 8080
# Healthcheck sencillo contra Spring Boot: no usamos actuator para no
# añadir dependencias; /api/v1/version es siempre 200 (público) y rápido.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/v1/version || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
