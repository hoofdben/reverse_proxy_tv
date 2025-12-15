Deployment notes for Portainer + Traefik

Traefik
- Entrypoints: web (:80) redirected to websecure (:443)
- ACME resolver: myresolver (HTTP-01), email: mathijsvandekerkhof@gmail.com
- External Docker network: traefik_default

Stack
- Services: api (Spring Boot), postgres (16-alpine)
- Secrets: POSTGRES_PASSWORD (map via Portainer secret in production)
- Labels route api.mallepetrus.nl to api on port 8080

Steps
1. Create a new Stack in Portainer and paste docker-compose.yml from the repo.
2. Ensure external network traefik_default exists and is used by the stack.
3. Create Portainer secret POSTGRES_PASSWORD and attach it to the stack.
4. Keep SPRING_PROFILES_ACTIVE=prod and set POSTGRES_* env vars as desired.
5. Deploy. Traefik issues certs via myresolver.

Post-deploy checks
- GET https://api.mallepetrus.nl/ returns status ok payload from HelloController
- GET https://api.mallepetrus.nl/actuator/health returns UP
- Flyway applied V1__init.sql in Postgres
