FROM gradle:8.12-jdk21 AS builder

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY gradlew ./

COPY src ./src
RUN gradle shadowJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S usergroup && adduser -S defaultuser -G usergroup
USER defaultuser

WORKDIR /app

COPY --from=builder --chown=defaultuser:usergroup /app/build/libs/*.jar /app/app.jar

ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar"]