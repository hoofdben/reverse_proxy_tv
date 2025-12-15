# syntax=docker/dockerfile:1.7-labs

ARG JAVA_VERSION=21

FROM eclipse-temurin:${JAVA_VERSION}-jre AS runtime

WORKDIR /app

# Install tini (proper signal handling) and curl (used by healthchecks)
USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends tini curl \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN useradd -r -u 10001 appuser

# Copy application JAR and entrypoint
COPY build/libs/*.jar /app/app.jar
COPY docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh \
    && chown -R appuser:appuser /app

EXPOSE 8080

USER appuser

# Healthcheck will be configured in docker-compose using curl

ENTRYPOINT ["/usr/bin/tini","-g","--","/app/entrypoint.sh"]
