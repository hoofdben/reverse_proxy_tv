### VPS deployment plan (step-by-step)

Below is a pragmatic, production-leaning checklist to deploy this project on a VPS using Docker, Traefik, and (optionally) Portainer. Adjust domain names and paths as needed.

#### 1) Prerequisites on the VPS
- OS: a recent Debian/Ubuntu or similar.
- Install:
  - Docker Engine (latest stable)
  - Docker Compose V2 (`docker compose version` should work)
  - Optional: Portainer (if you prefer a UI for stacks)
- DNS: point `api.your-domain.tld` to your VPS public IP.
- Open firewall ports:
  - 80/tcp (HTTP) and 443/tcp (HTTPS) for Traefik
  - 22/tcp (SSH)

#### 2) Set up Traefik (reverse proxy + TLS)
- Create a `docker-compose.yml` for Traefik (separate project/stack) that:
  - Exposes entrypoints `web` (80) and `websecure` (443), redirecting HTTP→HTTPS
  - Configures ACME (Let’s Encrypt) HTTP-01 with your email (e.g., `myresolver`)
  - Creates an external network named `traefik_default`
- Bring Traefik up:
  - `docker compose up -d` in its folder
  - Confirm the `traefik_default` network exists: `docker network ls`

Tip: If you already run Traefik, skip this step and just ensure the external network name matches `traefik_default` (or update this project’s compose to your network).

#### 3) Prepare secrets and keys on your workstation
Create a local `secrets/` directory in the project root with these files:
- `POSTGRES_PASSWORD.txt`
  - Strong DB password (e.g., 24+ chars)
- `APP_ENC_MASTER_KEY.txt`
  - Base64-encoded 32-byte key (256-bit). Generate with:
  - Linux/macOS:
    - `head -c 32 /dev/urandom | base64`
  - Windows PowerShell:
    - `[Convert]::ToBase64String((1..32 | ForEach-Object {Get-Random -Maximum 256}))`
- `JWT_PRIVATE_KEY.pem` and `JWT_PUBLIC_KEY.pem`
  - Generate RSA 2048-bit:
  - `openssl genpkey -algorithm RSA -out JWT_PRIVATE_KEY.pem -pkeyopt rsa_keygen_bits:2048`
  - `openssl rsa -pubout -in JWT_PRIVATE_KEY.pem -out JWT_PUBLIC_KEY.pem`

Keep these files private. They will be mounted as Docker secrets.

#### 4) Configure project for your domain
- In `docker-compose.yml`, under `api.labels`, change host rule:
  - `traefik.http.routers.rptv-api.rule=Host(`api.your-domain.tld`)`
- Ensure the external network matches your Traefik network (default here: `traefik_default`).

#### 5) Build the application image
- On your workstation (faster) or on VPS:
  - Build JAR: `./gradlew clean bootJar`
  - Build Docker image: `docker build -t reverse-proxy-tv:local .`
- If building locally and pushing to the VPS:
  - Option A: Save and copy
    - `docker save reverse-proxy-tv:local | gzip > rptv.tar.gz`
    - Copy to VPS: `scp rptv.tar.gz user@vps:/tmp/`
    - On VPS: `gunzip -c /tmp/rptv.tar.gz | docker load`
  - Option B: Push to a registry
    - Tag and push to your private registry or Docker Hub; then pull on VPS.

#### 6) Copy deployment files to VPS
- Copy the project folder (or only needed files) to the VPS, including:
  - `docker-compose.yml`
  - `secrets/` directory with the four secret files
  - The built image (if using save/load), otherwise you’ll pull it

Example: `rsync -av --exclude build --exclude .git ./ user@vps:/opt/reverse-proxy-tv/`

#### 7) Verify compose and secrets on VPS
- SSH to VPS, go to `/opt/reverse-proxy-tv/` (or your chosen path)
- Ensure `secrets/` folder contains:
  - `POSTGRES_PASSWORD.txt`
  - `APP_ENC_MASTER_KEY.txt`
  - `JWT_PRIVATE_KEY.pem`
  - `JWT_PUBLIC_KEY.pem`
- Confirm the Traefik external network exists: `docker network ls | grep traefik_default`

#### 8) Start the stack
- Launch: `docker compose up -d`
- This will start two services:
  - `postgres` (with secret-managed password)
  - `api` (Spring Boot service), on Traefik network, with TLS via Let’s Encrypt

The image uses a secrets-aware entrypoint, so it reads:
- `POSTGRES_PASSWORD_FILE` → `POSTGRES_PASSWORD`
- `APP_ENC_MASTER_KEY_FILE` → `APP_ENC_MASTER_KEY`
- Validates `JWT_PUBLIC_KEY_PATH` and `JWT_PRIVATE_KEY_PATH`

#### 9) Health checks & verification
- Check service health:
  - `docker compose ps`
- Logs: `docker compose logs -f api` (until you see “Started …”)
- Verify endpoints:
  - Health: `curl -i https://api.your-domain.tld/actuator/health` → should be `UP`
  - Root: `curl -i https://api.your-domain.tld/` → HelloController payload
- Verify Flyway migrations applied (in logs) and Postgres is healthy (`docker compose logs postgres`)

#### 10) Create an invite and register a user
- Temporarily (first-run) you can insert an invite directly into DB or expose an admin user:
  - Easiest: manually add an invite code in the DB using `docker exec -it <postgres-container> psql -U rptv -d rptv`:
    - `INSERT INTO invite_codes (id, code, max_uses, uses, created_at) VALUES (gen_random_uuid(), 'YOURCODE', 1, 0, now());`
  - Then register:
    - `POST https://api.your-domain.tld/api/auth/register` with JSON `{"email":"you@example.com","password":"Passw0rd!","inviteCode":"YOURCODE"}`
  - Or, if you already have an admin with `ROLE_ADMIN`, use the admin invites endpoints:
    - `POST /api/admin/invites?maxUses=1` (requires admin JWT)

#### 11) Routine operations
- See logs: `docker compose logs -f api`
- Restart API only: `docker compose restart api`
- Update image:
  - Build/push a new tag, update `docker-compose.yml` image reference, `docker compose up -d` (Compose will recreate api)
- Backups:
  - Postgres volume `pg_data` holds your DB. Use `pg_dump` or snapshot the volume.

#### 12) Security and hardening checklist
- Do not commit secrets to git. Keep `secrets/` directory out of VCS.
- Rotate `APP_ENC_MASTER_KEY` and JWT keys periodically; plan for key rotation (future enhancement supports multi-key if added).
- Keep `SPRING_PROFILES_ACTIVE=prod`.
- Ensure only required actuator endpoints are exposed (currently: `health`, `info`).
- Use strong `POSTGRES_PASSWORD` and restrict external DB access (only inside Docker network).
- Run Traefik with fail2ban/iptables or upstream protections as needed.

#### 13) Optional: Portainer deployment
- Deploy Traefik and this stack via Portainer “Stacks”.
- Before deploy, create Portainer secrets matching files above and reference them in the stack.
- Ensure the external network `traefik_default` is selected.

#### 14) Optional: CI/CD
- Add a GitHub Actions workflow to:
  - Build and run tests (`./gradlew test`)
  - Build and push Docker image (tagged with commit SHA or semver)
  - Deploy via SSH or Portainer API

#### 15) Troubleshooting
- 404/SSL issues: confirm DNS points to VPS, Traefik has ACME configured, and router rule matches your host.
- 401 Unauthorized: ensure you pass the `Authorization: Bearer <accessToken>` header.
- API fails at boot: check logs for missing `APP_ENC_MASTER_KEY` or missing RSA key paths. The entrypoint will log explicit errors and exit.
- Testcontainers tests won’t run on VPS without Docker-in-Docker: that’s expected; run in CI or a workstation with Docker.

### Quick command summary
- Build JAR: `./gradlew clean bootJar`
- Build image: `docker build -t reverse-proxy-tv:local .`
- Start stack: `docker compose up -d`
- Logs: `docker compose logs -f api`
- Health: `curl -fsS https://api.your-domain.tld/actuator/health`

---

## Deploy without cloning/building (Docker Compose pulls the image)

If you don’t want to clone the repository or build the image locally, you can deploy using a prebuilt container image referenced by a release compose file.

Prerequisites:
- Docker and Docker Compose installed on the VPS
- Traefik stack already running with external Docker network `traefik_default`

Steps:
1) Create a folder and enter it:
   - `mkdir -p /opt/reverse-proxy-tv && cd /opt/reverse-proxy-tv`

2) Create the secrets directory and required files:
   - `mkdir -p secrets`
   - `secrets/POSTGRES_PASSWORD.txt` — strong DB password
   - `secrets/APP_ENC_MASTER_KEY.txt` — Base64-encoded 32-byte key
   - `secrets/JWT_PRIVATE_KEY.pem` and `secrets/JWT_PUBLIC_KEY.pem` — RSA keys

3) Download the release compose file:
   - `curl -fsSL https://raw.githubusercontent.com/<your-org-or-user>/reverse_proxy_tv/main/docker-compose.release.yml -o docker-compose.yml`

4) Edit the compose file:
   - Replace the image reference `ghcr.io/your-org/reverse-proxy-tv:latest` with your published image if different.
   - Update the Traefik host label to your domain: `api.your-domain.tld`.

5) Start the stack:
   - `docker compose up -d`

6) Verify health:
   - `docker compose ps`
   - `curl -fsS https://api.your-domain.tld/actuator/health`

Publishing the image (CI recommended):
- See `.github/workflows/release.yml` in this repository for a ready-to-use GitHub Actions workflow that builds and pushes the image to GitHub Container Registry (GHCR) on pushes to `main`.

If you share your actual domain and whether you already have Traefik running, I can tailor the exact Traefik compose and finalize `docs/DEPLOYMENT.md` accordingly.
