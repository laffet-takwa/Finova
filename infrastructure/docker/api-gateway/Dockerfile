# syntax=docker/dockerfile:1
# -----------------------------------------------------------------------
# FINOVA — API Gateway
# Multi-stage: resolve dependencies once in a warm layer, then build only
# the module we ship. The build context is the repository root so the
# reactor POMs are reachable.
# -----------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Parent POM + every POM in this module's dependency closure, so dependency
# resolution is cached and only invalidated when a POM actually changes.
COPY backend/pom.xml ./backend/pom.xml
COPY backend/finova-common/pom.xml ./backend/finova-common/pom.xml
COPY backend/api-gateway/pom.xml ./backend/api-gateway/pom.xml
RUN mvn -B -q -pl api-gateway -am dependency:go-offline -DskipTests || true

COPY backend ./backend
RUN mvn -B -pl api-gateway -am package -DskipTests -Dmaven.test.skip=true \
    && cp backend/api-gateway/target/api-gateway.jar /build/app.jar

FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache wget curl \
    && addgroup -S finova && adduser -S -G finova finova
WORKDIR /app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -XX:+ExitOnOutOfMemoryError \
-Djava.security.egd=file:/dev/./urandom -Dfile.encoding=UTF-8"
ENV SERVER_PORT=8080
ENV SPRING_PROFILES_ACTIVE=dev

COPY --from=build /build/app.jar /app/app.jar
USER finova

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]