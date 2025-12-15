# Reverse Proxy TV – Backend Plan (Spring Boot + Gradle + Traefik + Postgres)

This document captures the step‑by‑step implementation plan to build and deploy an invite‑only Xtream reverse proxy API at `api.mallepetrus.nl`, running behind Traefik and deployed via Portainer Stack UI.

Assumptions confirmed or set by default (adjustable):
- Domain/DNS: `api.mallepetrus.nl` points to the Traefik host.
- Traefik:
  - Entrypoints: `web` (:80) with redirect to `websecure` (:443), `websecure` enabled.
  - ACME resolver name: `myresolver` (HTTP challenge on `web`).
  - External Docker network to join: `traefik_default`.
- Tech stack: Java 21, Spring Boot 3.x, Gradle (Kotlin DSL), Postgres 16, Flyway.
- Security defaults: Argon2id for password hashing; JWT RS256 with refresh tokens; app‑level encryption for Xtream credentials using AES‑GCM with a master key from a secret.
- Initial scope for Xtream proxy: `player_api.php` and pass‑through for `/live`, `/movie`, `/series` (no caching/rewrites in v1).
- Email features (verification/reset): deferred to later phase.

---

1. Project scaffolding and repository setup
   - Initialize Spring Boot project (Java 21, Gradle Kotlin DSL, Spring Web, Spring Security, Spring Data JPA, Validation, Actuator, Flyway, PostgreSQL driver).
   - Establish package structure: `nl.mallepetrus.rptv` (Reverse Proxy TV).
   - Add `.editorconfig`, `.gitattributes`, `.gitignore` tuned for Java/Gradle.
   - Configure `build.gradle.kts` with reproducible builds, versioning, and dependency constraints.

2. Security and authentication model
   - Configure Spring Security with stateless JWT auth, HTTP‑only cookie refresh tokens, CORS rules, and CSRF settings appropriate for API usage.
   - Password hashing via Argon2id; minimum password policy and validation.
   - JWT: RS256 keypair loaded from secrets; implement token issuance, rotation, and revocation on logout (optional blacklist in memory for v1; Redis later).
   - Invitation codes: admin‑only endpoints to generate single‑use or limited‑use codes with expiry; validate on registration.
   - Bootstrap first admin via environment variables on first run (email + temp password); idempotent.

3. Persistence and migrations
   - Define entities and Flyway migrations for:
     - `users` (id, email unique, password_hash, roles, status, created_at, updated_at, last_login_at).
     - `invite_codes` (code, created_by, expires_at, max_uses, used_count, created_at).
     - `xtream_accounts` (id, user_id FK, label, server_url, username, password_encrypted, user_agent_override, created_at, updated_at).
     - `audit_logs` (id, user_id nullable, action, meta JSONB, created_at). [Optional but recommended]
   - Add indices and constraints; enforce referential integrity.
   - Application‑level encryption:
     - AES‑GCM sealed fields for Xtream credentials using a master key from secret `RPTV_ENCRYPTION_KEY` (base64).
     - Key management abstraction to facilitate rotation later.

4. Xtream reverse proxy layer (v1)
   - Store user‑provided Xtream account configurations (validated on save with a lightweight connectivity check where feasible).
   - Implement pass‑through proxy endpoints:
     - `GET /proxy/{accountId}/player_api.php` → maps to upstream `player_api.php` with the user’s Xtream credentials.
     - `GET /proxy/{accountId}/live/...`, `.../movie/...`, `.../series/...` → streamed pass‑through with sane timeouts.
   - Attach per‑request attribution (user id, account id) for audit logging.
   - Timeouts/retries and upstream connection pooling; no response rewriting or caching in v1.

5. Public API surface (v1)
   - Auth:
     - `POST /auth/register` (invite code required)
     - `POST /auth/login`
     - `POST /auth/refresh`
     - `POST /auth/logout`
   - Admin:
     - `POST /admin/invites` (create code)
     - `GET /admin/invites` (list)
   - User Xtream accounts:
     - `POST /me/xtream-accounts`
     - `GET /me/xtream-accounts`
     - `GET /me/xtream-accounts/{id}`
     - `PUT /me/xtream-accounts/{id}`
     - `DELETE /me/xtream-accounts/{id}`
   - Proxy:
     - `GET /proxy/{accountId}/player_api.php`
     - `GET /proxy/{accountId}/(live|movie|series)/**`

6. Configuration and secrets
   - `application.yml` with profiles: `default`, `prod`.
   - Environment variables (wired via Portainer):
     - Database: `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` (prefer Portainer secrets).
     - JWT: `JWT_PRIVATE_KEY_PEM`, `JWT_PUBLIC_KEY_PEM` (Portainer secrets).
     - Encryption: `RPTV_ENCRYPTION_KEY` (base64) (Portainer secret).
     - Admin bootstrap: `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`.
   - Strict configuration validation at startup; fail fast if required secrets are missing in `prod`.

7. Observability & logging
   - Expose `/actuator/health` and `/actuator/info` (no Traefik route in prod by default).
   - Optional `/actuator/prometheus` if metrics are needed.
   - JSON logs to stdout with request correlation IDs; log sanitization for PII/credentials.

8. Docker & Portainer (compose v3)
   - Multi‑stage Dockerfile for the app (builder + runtime with distroless/temurin base).
   - `docker-compose.yml` services:
     - `api` (the Spring app)
     - `postgres` (unless using external DB) with named volume `pg_data`
   - Networks:
     - Internal app network (default compose network).
     - External `traefik_default` network (declared as external) for Traefik routing.
   - Traefik labels on `api`:
     - `traefik.enable=true`
     - `traefik.http.routers.rptv-api.rule=Host(`api.mallepetrus.nl`)`
     - `traefik.http.routers.rptv-api.entrypoints=websecure`
     - `traefik.http.routers.rptv-api.tls.certresolver=myresolver`
     - `traefik.http.services.rptv-api.loadbalancer.server.port=8080`
   - No published ports from the app; only Traefik routes.

9. Testing strategy
   - Unit tests for services (auth, invite, encryption, proxy config builder).
   - Integration tests against Testcontainers Postgres for repositories and Flyway.
   - Smoke test for auth flow (register with invite → login → refresh → logout).

10. Deployment via Portainer Stack UI
   - Create Portainer secrets for: DB password, `RPTV_ENCRYPTION_KEY`, `JWT_PRIVATE_KEY_PEM`, `JWT_PUBLIC_KEY_PEM`.
   - Deploy the compose stack; ensure it joins `traefik_default` network.
   - Verify Traefik picks up router: `rptv-api` at `https://api.mallepetrus.nl` with resolver `myresolver`.
   - Run DB migrations automatically via Flyway on app start.
   - Bootstrap admin on first start (rotate temp password after login).

11. Post‑deploy checks
   - Health check endpoints up; logs clean.
   - Registration with invite works; login and token refresh succeed.
   - Add Xtream account and successfully call `player_api.php` through proxy.
   - Verify TLS and headers (no sensitive info in logs/headers).

12. Future phases (vNext)
   - Email verification and password reset flows (SMTP integration).
   - Rate limiting (per user/IP) and caching layer for selected endpoints.
   - Admin UI (or extended API) for users/invites/audit.
   - Encryption key rotation process; HSM or secret manager.
   - Redis for token blacklist and rate limiting.

---

Deliverables
- Source repository with the application code, tests, Dockerfile, and `docker-compose.yml` ready for Portainer.
- Documentation: README with setup, secrets, and Portainer deployment steps.
- Initial admin bootstrap and invite flow implemented.

Risks & notes
- Upstream Xtream providers vary; ensure configurable timeouts and robust error handling.
- Avoid storing plaintext credentials; only encrypted fields at rest and minimized logging.
- For streaming endpoints, validate performance under expected load; consider connection tuning.
