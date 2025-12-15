# syntax=docker/dockerfile:1.7-labs

ARG JAVA_VERSION=21

FROM eclipse-temurin:${JAVA_VERSION}-jre AS runtime

WORKDIR /app

# The JAR will be copied in at build time by docker-compose or external build
COPY build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]
