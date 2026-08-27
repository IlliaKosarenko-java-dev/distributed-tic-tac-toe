# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml lombok.config ./
COPY game-engine-service/pom.xml game-engine-service/
COPY game-session-service/pom.xml game-session-service/
COPY api-gateway/pom.xml api-gateway/
COPY integration-tests/pom.xml integration-tests/

COPY game-session-service/src game-session-service/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl game-session-service -am -DskipTests package

RUN cp $(ls game-session-service/target/*-exec.jar 2>/dev/null || ls game-session-service/target/*.jar | grep -v original) /build/app.jar

FROM bellsoft/liberica-openjre-alpine:17
WORKDIR /app

RUN addgroup -S app && adduser -S -G app app
COPY --from=build --chown=app:app /build/app.jar app.jar
USER app

EXPOSE 8082

HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8082/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
