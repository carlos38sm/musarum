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
# JVM tuneada para portátil viejo: 256 MB max, usando los contenedores
# cgroups para no comerse toda la RAM. Si tu portátil tiene más, sube el
# valor en docker-compose.yml con JAVA_TOOL_OPTIONS.
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx384m -XX:+UseSerialGC"
EXPOSE 8080
# Healthcheck sencillo contra Spring Boot: no usamos actuator para no
# añadir dependencias; /api/v1/version es siempre 200 (público) y rápido.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/v1/version || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
