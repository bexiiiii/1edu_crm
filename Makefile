.PHONY: help build clean test infra-up infra-down proto all \
        services-up services-down services-logs \
        deploy deploy-service \
        backup setup-cron restore \
        logs ps

COMPOSE_PROD=docker compose -f docker-compose.prod.yml -p 1edu

help:
	@echo ""
	@echo "  1edu CRM — Available commands"
	@echo "  ──────────────────────────────────────────────────────"
	@echo "  Development"
	@echo "    make build              Build all service JARs (skip tests)"
	@echo "    make clean              Clean build artifacts"
	@echo "    make test               Run all tests"
	@echo "    make proto              Generate gRPC code from proto files"
	@echo ""
	@echo "  Production Deploy"
	@echo "    make deploy             Full deploy: build JARs → Docker → ordered startup"
	@echo "    make deploy-service s=X Rebuild & restart one service (e.g. s=notification-service)"
	@echo "    make ps                 Show running containers and status"
	@echo "    make logs s=X           Tail logs of a service (e.g. s=tenant-service)"
	@echo ""
	@echo "  Backup & Recovery"
	@echo "    make backup             Run backup now (PostgreSQL + MongoDB)"
	@echo "    make setup-cron         Install auto-backup cron job (run once on server)"
	@echo "    make restore-pg         Restore PostgreSQL from latest backup"
	@echo "    make restore-mongo      Restore MongoDB from latest backup"
	@echo ""
	@echo "  Infrastructure"
	@echo "    make infra-up           Start infrastructure only (Docker Compose)"
	@echo "    make infra-down         Stop infrastructure"
	@echo "    make infra-reset        Wipe volumes + restart infrastructure"
	@echo "    make infra-logs         Stream infrastructure logs"
	@echo ""

# ─── Build ────────────────────────────────────────────────────────────────────
build:
	./gradlew build -x test --no-daemon

clean:
	./gradlew clean

test:
	./gradlew test

proto:
	./gradlew :services:common-proto:generateProto

all: proto build
	@echo "Build complete. Run 'make deploy' to package and start all services."

# ─── Deploy (production — ordered startup with healthchecks) ─────────────────
# Full deploy: build JARs → build images → ordered container startup
deploy:
	@./deploy.sh full

# Deploy single service without rebuilding others:
#   make deploy-service s=notification-service
deploy-service:
	@test -n "$(s)" || (echo "Usage: make deploy-service s=<service-name>" && exit 1)
	@./deploy.sh restart $(s)

# Container status
ps:
	@./deploy.sh status

# Tail logs: make logs s=tenant-service
logs:
	@test -n "$(s)" || (echo "Usage: make logs s=<service-name>" && exit 1)
	@./deploy.sh logs $(s)

# ─── Backup & Recovery ────────────────────────────────────────────────────────
backup:
	@./deploy.sh backup

setup-cron:
	@bash scripts/cron-setup.sh

# Restore PostgreSQL from latest backup (DESTRUCTIVE — stops services first)
restore-pg:
	@echo "WARNING: This will restore PostgreSQL from the latest backup."
	@echo "All current data will be REPLACED."
	@read -p "Type 'yes' to confirm: " confirm && [ "$$confirm" = "yes" ] || exit 1
	@$(COMPOSE_PROD) stop
	@echo "Restoring PostgreSQL from /backups/1edu_crm/postgres_latest.sql.gz ..."
	@bash -lc 'set -a; source .env; set +a; \
		zcat /backups/1edu_crm/postgres_latest.sql.gz | docker exec -i 1edu-postgres \
		env PGPASSWORD="$$DB_PASSWORD" psql -U "$$DB_USERNAME" -d "$$DB_NAME"'
	@echo "Restore complete. Run 'make deploy' to restart services."

# Restore MongoDB from latest backup (DESTRUCTIVE)
restore-mongo:
	@echo "WARNING: This will restore MongoDB from the latest backup."
	@read -p "Type 'yes' to confirm: " confirm && [ "$$confirm" = "yes" ] || exit 1
	@docker cp /backups/1edu_crm/mongodb_latest.tar.gz 1edu-mongodb:/tmp/
	@docker exec 1edu-mongodb bash -c "cd /tmp && tar -xzf mongodb_latest.tar.gz && mongorestore /tmp/mongodb_latest/ --drop"
	@echo "Restore complete."

# ─── Infrastructure ───────────────────────────────────────────────────────────
infra-up:
	./deploy.sh infra

infra-down:
	$(COMPOSE_PROD) down

infra-reset:
	$(COMPOSE_PROD) down -v
	./deploy.sh infra

infra-logs:
	$(COMPOSE_PROD) logs -f postgres redis rabbitmq

# ─── Local dev: run services via Gradle (not Docker) ─────────────────────────
services-up:
	@bash scripts/start-services.sh

services-down:
	@bash scripts/stop-services.sh

services-logs:
	@test -n "$(s)" || (echo "Usage: make services-logs s=<service-name>" && exit 1)
	@tail -f logs/$(s).log

registry:
	./gradlew :services:service-registry:bootRun

gateway:
	./gradlew :services:api-gateway:bootRun

student:
	./gradlew :services:student-service:bootRun

tenant:
	./gradlew :services:tenant-service:bootRun
