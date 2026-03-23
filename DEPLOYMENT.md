# Production Deployment

This repository is prepared for a production deployment on `api.1edu.kz` with tenant subdomains under `*.1edu.kz`.

## Domain model

- `API_DOMAIN=api.1edu.kz` is the public entrypoint for the backend and Keycloak.
- `BASE_DOMAIN=1edu.kz` is the tenant base domain used by the gateway and cert-issuer.
- `KEYCLOAK_INTERNAL_URL=http://keycloak:8080/auth` is used inside Docker.
- `KEYCLOAK_PUBLIC_URL=https://api.1edu.kz/auth` is used as the public issuer (`iss`) in JWTs.

## Clean server bootstrap

Run this once on a fresh Ubuntu host:

```bash
sudo bash scripts/bootstrap-server.sh
```

What it does:

- installs Docker Engine and Docker Compose plugin
- installs OpenJDK 21, `git`, `make`, `ufw`, and basic admin tools
- creates a 4 GB swapfile if the server has no swap
- configures `vm.max_map_count=262144` for Elasticsearch
- opens only `22/tcp`, `80/tcp`, and `443/tcp`
- creates the external Docker network `1edu_1edu-network`

## First deploy

```bash
cd /opt
git clone https://github.com/bexiiiii/1edu_crm.git
cd 1edu_crm
cp .env.production .env
```

Fill in secrets in `.env` and set at least:

```bash
BASE_DOMAIN=1edu.kz
API_DOMAIN=api.1edu.kz
CERT_EMAIL=admin@1edu.kz
KEYCLOAK_INTERNAL_URL=http://keycloak:8080/auth
KEYCLOAK_PUBLIC_URL=https://api.1edu.kz/auth
```

Then deploy:

```bash
./deploy.sh full
./scripts/cron-setup.sh --no-prompt
```

`./deploy.sh full` will:

- build all Spring Boot JARs
- issue the first Let's Encrypt certificate for `api.1edu.kz`
- start infrastructure in the correct order
- start application services and nginx
- verify `https://api.1edu.kz/health`

## Ongoing operations

Full redeploy:

```bash
./deploy.sh full
```

Restart one service:

```bash
./deploy.sh restart notification-service
```

Tail logs:

```bash
./deploy.sh logs api-gateway
```

Backups:

```bash
./deploy.sh backup
```

Certificate renewal:

```bash
./scripts/renew-certs.sh
```

## Verification checklist

```bash
curl -I https://api.1edu.kz/health
./deploy.sh status
docker compose -f docker-compose.prod.yml -p 1edu ps
```

Expected:

- `https://api.1edu.kz/health` returns `200 OK`
- `nginx`, `api-gateway`, `auth-service`, `tenant-service`, and `keycloak` are up
- internal ports stay bound to `127.0.0.1`, only `80/443` are public
