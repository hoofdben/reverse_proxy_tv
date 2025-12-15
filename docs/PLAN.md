# Implementation Plan — Auth, Encrypted Xtream Accounts, and Proxy Endpoints (v1)

This plan delivers invite‑only authentication (JWT), user and Xtream account entities with application‑level encryption at rest, and the initial Xtream reverse‑proxy endpoints. It builds on the current Spring Boot 3 + Java 21 + Gradle scaffold and the Traefik/Portainer deployment described in docs/DEPLOYMENT.md.

## 1. Security & Authentication
1.1 Password hashing
- Use Argon2id via Spring Security crypto (delegating password encoder configured for Argon2id only).

1.2 JWT model
- Use RS256 signing with a key pair provided via environment/secrets: `JWT_PRIVATE_KEY_PEM`, `JWT_PUBLIC_KEY_PEM`.
- Access tokens short‑lived (e.g., 10m). Refresh tokens longer (e.g., 14d) with rotation on each refresh.
- Store refresh tokens server‑side (DB) with status and expiry to support revocation and rotation.

1.3 Invite‑only registration
- Admin‑only endpoints to create invite codes (single‑use by default, configurable uses and expiry).
- Registration requires a valid invite code; consume/increment usage on success.

1.4 Endpoints (public unless noted)
- POST `/api/auth/register` — body: email, password, inviteCode. Creates user; returns 201.
- POST `/api/auth/login` — body: email, password. Issues access+refresh tokens (HTTP‑only cookies by default) or in JSON payload (configurable).
- POST `/api/auth/refresh` — uses refresh token; rotates token pair.
- POST `/api/auth/logout` — revokes current refresh token.
- GET `/api/auth/me` — returns current user profile; requires access token.
- Admin: POST `/api/admin/invites` (create), GET `/api/admin/invites` (list), DELETE `/api/admin/invites/{id}` (revoke).

1.5 Spring Security configuration
- Stateless security; CSRF disabled for API; session creation policy STATELESS.
- Permit `/`, `/actuator/health`, `/actuator/info`, `/api/auth/*`.
- Require `ROLE_ADMIN` for `/api/admin/**`.
- JWT authentication filter (once‑per‑request) validates RS256 signature and loads user.

## 2. Data Model & Persistence
2.1 Entities
- User: `id (UUID)`, `email (unique)`, `passwordHash`, `roles (string set or role table)`, `status (ACTIVE/LOCKED)`, `lastLoginAt`, `createdAt`, `updatedAt`.
- InviteCode: `id (UUID)`, `code (unique)`, `createdBy (FK User)`, `expiresAt (nullable)`, `maxUses (int, default 1)`, `usedCount (int)`, `createdAt`.
- RefreshToken: `id (UUID)`, `userId (FK)`, `tokenHash` (hash refresh token with SHA‑256), `expiresAt`, `revokedAt`, `createdAt`, `replacedBy (nullable)`.
- XtreamAccount: `id (UUID)`, `userId (FK)`, `label`, `serverUrl`, `usernameEnc`, `passwordEnc`, `iv`, `createdAt`, `updatedAt`, `userAgentOverride (nullable)`.

2.2 Encryption at rest
- Application‑level encryption using AES‑GCM.
- Master key supplied in env/secret: `APP_ENC_MASTER_KEY` (base64 bytes). Key rotation supported via `APP_ENC_MASTER_KEY_PREV` for seamless re‑encryption.
- Implement `CryptoService` (AES‑GCM with HKDF or per‑record random IVs). Store ciphertext + IV + auth tag. Provide JPA AttributeConverters for encrypted strings to ensure consistent column handling (`usernameEnc`, `passwordEnc`).

2.3 Flyway migrations
- V2: create tables `users`, `invite_codes`, `refresh_tokens`, `xtream_accounts` with indexes and constraints.
- V3: add initial admin bootstrap if configured via env (optional seed).

## 3. Xtream Accounts Management API
- POST `/api/xtream/accounts` — add an account (label, serverUrl, username, password, optional UA). Encrypt secrets before persist.
- GET `/api/xtream/accounts` — list accounts for current user (mask secrets; no decryption in list).
- GET `/api/xtream/accounts/{id}` — detail (still never return raw password; may return username decrypted if needed? default: no).
- DELETE `/api/xtream/accounts/{id}` — remove.
- POST `/api/xtream/accounts/{id}/test` — connectivity test (HEAD/GET `/player_api.php?username=...&password=...` upstream) with timeout, returns upstream status (sanitized).

## 4. Xtream Proxy (v1)
4.1 Scope (pass‑through only)
- Support common endpoints: `GET /proxy/{accountId}/player_api.php` and streaming paths `GET /proxy/{accountId}/live/**`, `/proxy/{accountId}/movie/**`, `/proxy/{accountId}/series/**`.
- Build upstream URL from stored account and incoming path/query; inject user credentials when required (e.g., player_api.php can accept `username`/`password` query params).

4.2 Implementation
- Use Spring WebFlux `WebClient` for non‑blocking proxying even in MVC app (add `spring-boot-starter-webflux`). Alternatively, stick to `RestTemplate`/`HttpClient` with stream support; prefer WebClient.
- Enforce per‑request timeout (e.g., 15s) and reasonable max header size.
- Sanitize headers: strip Hop‑by‑Hop headers; optionally set `User-Agent` from account override.
- Stream response body and propagate status codes. Do not cache in v1.
- Access control: only owner of the `accountId` can use the proxy for that account.

4.3 Rate limiting (optional v1)
- Simple in‑memory token bucket per user (e.g., 60 req/min) to prevent abuse. Can be off by default.

## 5. Configuration & Secrets
- Add config properties under `rptv.*` for jwt expirations, cookie settings, proxy timeouts, rate limits.
- Secrets (via Portainer):
  - `POSTGRES_PASSWORD`
  - `JWT_PRIVATE_KEY_PEM`, `JWT_PUBLIC_KEY_PEM`
  - `APP_ENC_MASTER_KEY`
- Env for first admin bootstrap (optional): `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`.

## 6. Code Structure
- package `nl.mallepetrus.rptv.auth`: controllers, services (AuthService, InviteService, TokenService), jwt filter.
- package `nl.mallepetrus.rptv.crypto`: CryptoService, converters.
- package `nl.mallepetrus.rptv.user`: User entity/repo/service, admin endpoints.
- package `nl.mallepetrus.rptv.xtream`: XtreamAccount entity/repo/service, ProxyController, UpstreamClient.
- package `nl.mallepetrus.rptv.config`: SecurityConfig, WebClient config, properties.

## 7. Testing Strategy
- Unit: CryptoService (AES‑GCM), token hashing/validation, invite code logic.
- Integration: Auth flow (register/login/refresh/logout), Xtream account CRUD, proxy path construction.
- Testcontainers: Postgres for persistence tests.
- Smoke test: `/` and `/actuator/health`.

## 8. Deployment Updates (docker-compose)
- Mount secrets for JWT keys and `APP_ENC_MASTER_KEY`.
- Add env vars for token lifetimes and cookie flags.
- Keep Traefik labels (router host `api.mallepetrus.nl`, entrypoint `websecure`, `tls.certresolver=myresolver`).

## 9. Step‑by‑Step Execution
1) Add dependencies:
   - `spring-boot-starter-webflux` (for WebClient streaming)
   - `jjwt` or use Spring Security’s Nimbus JWT support (preferred: Nimbus via spring‑security‑oauth2‑jwt)
   - `org.bouncycastle:bcprov‑jdk18on` (optional, not required if using JCE AES‑GCM)
2) Implement CryptoService (AES‑GCM) + converters; wire master key from env.
3) Create entities and repositories; add Flyway V2 migration.
4) Configure Security: Argon2id encoder, JWT filter, access rules.
5) Implement AuthService + controllers (register/login/refresh/logout/me) with refresh token persistence/rotation.
6) Implement Invite management and admin endpoints.
7) Implement XtreamAccount service + CRUD endpoints; add connectivity test.
8) Implement ProxyController using WebClient streaming and header sanitation.
9) Add integration tests with Testcontainers; ensure migrations run and flows pass.
10) Update docker-compose to include new secrets/env; document in docs/DEPLOYMENT.md.
11) Build & deploy via Portainer; verify with Traefik (`api.mallepetrus.nl`).

## 10. Future Enhancements (post‑v1)
- Email verification and password reset (SMTP config).
- MFA (TOTP), account lockout policies, audit logs for admin actions.
- Caching for `player_api.php` responses; ranged streaming optimization.
- Rate limiting via Traefik plugin or Redis‑backed limiter.
- Key rotation tooling for `APP_ENC_MASTER_KEY` and JWT keypair.
