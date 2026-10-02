# -----------------------------------------------------------------------
# FINOVA — Spring Boot microservice (discovery-server, user-service,
# account-service, transaction-service, fraud-service, notification-service)
#
# Built with a shared ARG so every service uses one tested recipe:
#   docker build --build-arg MODULE=user-service --build-arg APP_PORT=8081 \
#     -f infrastructure/docker/service/Dockerfile .
# -----------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
ARG MODULE
WORKDIR /build

COPY backend/pom.xml ./backend/pom.xml
COPY backend/finova-common/pom.xml ./backend/finova-common/pom.xml
COPY backend/discovery-server/pom.xml ./backend/discovery-server/pom.xml
COPY backend/user-service/pom.xml ./backend/user-service/pom.xml
COPY backend/account-service/pom.xml ./backend/account-service/pom.xml
COPY backend/transaction-service/pom.xml ./backend/transaction-service/pom.xml
COPY backend/fraud-service/pom.xml ./backend/fraud-service/pom.xml
COPY backend/notification-service/pom.xml ./backend/notification-service/pom.xml
RUN mvn -B -q -pl ${MODULE} -am dependency:go-offline -DskipTests || true

COPY backend ./backend
RUN mvn -B -pl ${MODULE} -am package -DskipTests -Dmaven.test.skip=true \
    && cp backend/${MODULE}/target/${MODULE}.jar /build/app.jar

FROM eclipse-temurin:17-jre-alpine
ARG MODULE
ARG APP_PORT
RUN apk add --no-cache wget curl \
    && addgroup -S finova && adduser -S -G finova finova
WORKDIR /app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -XX:+ExitOnOutOfMemoryError \
-Djava.security.egd=file:/dev/./urandom -Dfile.encoding=UTF-8" \
    SERVER_PORT=${APP_PORT} \
    SPRING_PROFILES_ACTIVE=dev

COPY --from=build /build/app.jar /app/app.jar
USER finova

EXPOSE ${APP_PORT}
HEALTHCHECK --interval=15s --timeout=5s --start-period=75s --retries=5 \
  CMD wget -qO- http://localhost:${APP_PORT}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]