.PHONY: help build clean test infra-up infra-down proto all services-up services-down services-logs deploy deploy-service

help:
	@echo "1edu CRM - Available commands:"
	@echo ""
	@echo "  make build              - Build all service JARs (skip tests)"
	@echo "  make clean              - Clean build artifacts"
	@echo "  make test               - Run all tests"
	@echo "  make proto              - Generate gRPC code from proto files"
	@echo "  make infra-up           - Start infrastructure (Docker Compose)"
	@echo "  make infra-down         - Stop infrastructure"
	@echo "  make infra-logs         - Show infrastructure logs"
	@echo "  make deploy             - Build JARs + rebuild Docker images + restart all"
	@echo "  make deploy-service s=X - Rebuild and restart a single service (e.g. s=notification-service)"
	@echo "  make all                - Build and start everything"
	@echo ""

build:
	./gradlew build -x test

clean:
	./gradlew clean

test:
	./gradlew test

proto:
	./gradlew :services:common-proto:generateProto

infra-up:
	cd infrastructure && docker compose up -d

infra-down:
	cd infrastructure && docker compose down

infra-logs:
	cd infrastructure && docker compose logs -f

infra-reset:
	cd infrastructure && docker compose down -v && docker compose up -d

# Start individual services
registry:
	./gradlew :services:service-registry:bootRun

gateway:
	./gradlew :services:api-gateway:bootRun

student:
	./gradlew :services:student-service:bootRun

tenant:
	./gradlew :services:tenant-service:bootRun

# Start all services in background (run after infra-up)
services-up:
	@bash scripts/start-services.sh

# Stop all running services
services-down:
	@bash scripts/stop-services.sh

# Tail logs of a specific service: make services-logs s=student-service
services-logs:
	@tail -f logs/$(s).log

all: proto build
	@echo "Build complete. Start infrastructure with 'make infra-up'"
	@echo "Then run 'make services-up' to start all services."

# ─── Deploy (server-side build, then Docker package) ─────────
# Builds JARs with Gradle (uses server's Gradle cache — fast after first run),
# then packages into Docker images via Dockerfile.prebuilt (~10 sec per service).
deploy:
	docker compose down
	./gradlew build -x test --no-daemon
	docker compose build
	docker compose up -d

# Rebuild and restart a single service only:
#   make deploy-service s=notification-service
deploy-service:
	@test -n "$(s)" || (echo "Usage: make deploy-service s=<service-name>" && exit 1)
	docker compose stop $(s)
	./gradlew :services:$(s):bootJar -x test --no-daemon
	docker compose build --no-deps $(s)
	docker compose up -d --no-deps $(s)
