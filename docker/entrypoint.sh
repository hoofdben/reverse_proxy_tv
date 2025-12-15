#!/bin/sh
set -euo pipefail

# Translate *_FILE envs to plain envs if the plain ones are missing
if [ -n "${POSTGRES_PASSWORD_FILE:-}" ] && [ -z "${POSTGRES_PASSWORD:-}" ]; then
  if [ -r "$POSTGRES_PASSWORD_FILE" ]; then
    export POSTGRES_PASSWORD="$(cat "$POSTGRES_PASSWORD_FILE" | tr -d '\r\n')"
  fi
fi

if [ -n "${APP_ENC_MASTER_KEY_FILE:-}" ] && [ -z "${APP_ENC_MASTER_KEY:-}" ]; then
  if [ -r "$APP_ENC_MASTER_KEY_FILE" ]; then
    export APP_ENC_MASTER_KEY="$(cat "$APP_ENC_MASTER_KEY_FILE" | tr -d '\r\n')"
  fi
fi

# Validate required secrets/keys
missing=0
if [ -z "${POSTGRES_PASSWORD:-}" ]; then
  echo "[ENTRYPOINT] ERROR: POSTGRES_PASSWORD is not set (or POSTGRES_PASSWORD_FILE unreadable)" >&2
  missing=1
fi
if [ -z "${APP_ENC_MASTER_KEY:-}" ]; then
  echo "[ENTRYPOINT] ERROR: APP_ENC_MASTER_KEY is not set (or APP_ENC_MASTER_KEY_FILE unreadable)" >&2
  missing=1
fi
if [ -z "${JWT_PUBLIC_KEY_PATH:-}" ] || [ ! -r "${JWT_PUBLIC_KEY_PATH:-/nope}" ]; then
  echo "[ENTRYPOINT] ERROR: JWT_PUBLIC_KEY_PATH not set or file not readable" >&2
  missing=1
fi
if [ -z "${JWT_PRIVATE_KEY_PATH:-}" ] || [ ! -r "${JWT_PRIVATE_KEY_PATH:-/nope}" ]; then
  echo "[ENTRYPOINT] ERROR: JWT_PRIVATE_KEY_PATH not set or file not readable" >&2
  missing=1
fi

if [ "$missing" -ne 0 ]; then
  echo "[ENTRYPOINT] Failing fast due to missing configuration." >&2
  exit 1
fi

# Sane JVM defaults (can be overridden via JAVA_OPTS)
JAVA_OPTS="${JAVA_OPTS:-}"
JAVA_OPTS="$JAVA_OPTS -Djava.security.egd=file:/dev/./urandom"
JAVA_OPTS="$JAVA_OPTS -XX:+ExitOnOutOfMemoryError"

echo "[ENTRYPOINT] Starting app..."
exec java $JAVA_OPTS -jar /app/app.jar
