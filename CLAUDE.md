# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

1edu CRM — a multi-tenant CRM system for education centers, built as a Spring Boot microservices architecture. Group: `com.ondeedu`, Java 21, Gradle multi-module build.

## Build & Run Commands

```bash
make build          # Build all services (skips tests)
make test           # Run all tests
make clean          # Clean build artifacts
make proto          # Generate gRPC code from .proto files
make infra-up       # Start infrastructure via Docker Compose
make infra-down     # Stop infrastructure
make infra-reset    # Stop + wipe volumes + restart infrastructure
```

### Deploy on server (recommended — fast builds)

`docker-compose.yml` использует `Dockerfile.prebuilt` — JAR'ы не собираются внутри Docker, сервер сначала строит их через Gradle (используя свой кэш), затем Docker просто упаковывает (~10 сек на сервис):

```bash
# Первый раз (Gradle скачивает зависимости, ~5-10 мин):
git pull && make deploy

# Повторные деплои (Gradle кэш уже прогрет, ~1-2 мин на все):
git pull && make deploy

# Обновить один сервис (самый быстрый вариант):
make deploy-service s=notification-service
make deploy-service s=tenant-service
```

`make deploy` вызывает `scripts/deploy.sh` — умный скрипт с **поочерёдным запуском** и проверкой healthcheck на каждом шаге:
1. `./gradlew build -x test --no-daemon` — компилирует JAR'ы (кэш в `~/.gradle`)
2. `docker compose down` — останавливает старые контейнеры
3. `docker compose build` — Docker читает JAR'ы из `services/<name>/build/libs/` (~10 сек каждый)
4. Запуск по группам: infra → service-registry → api-gateway + auth → tenant + notification → all business

**Dockerfile.prebuilt** (используется по умолчанию): `FROM eclipse-temurin:21-jre` + копирует JAR + запускает с флагами `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Xss512k`.

**Полный Docker-build** (`Dockerfile`) нужен только в CI/CD где нет Gradle на хосте — занимает ~18 мин первый раз.

### Backup & Recovery

```bash
# Запустить резервное копирование вручную (PostgreSQL + MongoDB)
make backup

# Установить автоматическое резервное копирование (один раз на сервере)
make setup-cron    # устанавливает cron: ежедневно в 03:00

# Восстановить PostgreSQL из последнего бэкапа (ОСТОРОЖНО — перезаписывает данные)
make restore-pg

# Восстановить MongoDB
make restore-mongo
```

Бэкапы хранятся в `/backups/1edu_crm/` с retention 7 дней. Логи: `/var/log/1edu_cron.log`.

Cron jobs после `make setup-cron`:
- **03:00 ежедневно** — `scripts/backup.sh` (PostgreSQL `pg_dumpall` + MongoDB `mongodump`)
- **04:00 по воскресеньям** — `docker system prune` (чистка неиспользуемых образов/контейнеров)

### Run individual services
```bash
./gradlew :services:student-service:bootRun
./gradlew :services:lead-service:bootRun
# Pattern: ./gradlew :services:<service-name>:bootRun
```

### Build/test a single service
```bash
./gradlew :services:student-service:build
./gradlew :services:student-service:test
```

### Startup order
1. `make infra-up` (Postgres, Redis, RabbitMQ, Elasticsearch, Keycloak, MinIO, Zipkin, Prometheus, Grafana)
2. `service-registry` (Eureka on port 8761)
3. `api-gateway` (port 8090)
4. Business services (any order)

## Architecture

### Module Layout

- **`services/common`** — shared library (not bootable). Provides security config, tenant resolution, JPA config, Redis/RabbitMQ config, base entity, API response DTOs, exception handling, gRPC interceptors. All business services depend on this.
- **`services/common-proto`** — protobuf/gRPC definitions. Proto files in `src/main/proto/`. Generate with `make proto`.
- **`services/api-gateway`** — Spring Cloud Gateway (reactive). Routes all `/api/v1/*` to services via Eureka discovery with Resilience4j circuit breakers.
- **`services/service-registry`** — Eureka server for service discovery.
- **Business services**: student, course, lead, payment, finance, staff, task, schedule, notification, file, analytics, report, communication, auth, tenant, audit.
- **`services/audit-service`** (port 8130) — MongoDB-based audit log. Listens on RabbitMQ `audit.system.queue` and `audit.tenant.queue`. No JPA, no Flyway, no Redis, no gRPC. Excludes all RDBMS/Redis autoconfiguration.

### Multi-Tenancy (Schema-per-tenant)

Tenant isolation uses PostgreSQL schemas. Key flow:
1. `TenantInterceptor` (HTTP) / `GrpcTenantInterceptor` (gRPC) extracts tenant from `X-Tenant-ID` header or JWT `tenant_id` claim
2. `TenantContext` stores tenant ID, schema name (`tenant_<id>`), and user ID in ThreadLocals
3. `TenantIdentifierResolver` + `TenantSchemaConnectionProvider` set the Hibernate schema at connection level
4. Hibernate `multiTenancy: SCHEMA` is configured in each service's `application.yml`

Tenant schemas are created via `system.create_tenant_schema()` SQL function (see `infrastructure/init-scripts/postgres/01-init-schemas.sql`). The `system.tenants` table is the tenant registry.

### Inter-Service Communication

- **REST** — external clients go through the API gateway. All endpoints follow `/api/v1/<resource>` pattern.
- **gRPC** — used for synchronous service-to-service calls. Each service exposes gRPC on port `9xxx` (HTTP port + 1000, e.g. student: 8102/9102). Tenant context is propagated via `GrpcClientTenantInterceptor`.
- **RabbitMQ** — async events via topic exchanges. Exchanges/queues/routing keys defined in `RabbitMQConfig`. Pattern: `<domain>.exchange`, `<domain>.<event>.queue`, `<domain>.<event>` routing key.

### Service Port Map

| Service | HTTP | gRPC | Notes |
|---------|------|------|-------|
| service-registry | 8761 | — | Eureka |
| api-gateway | 8090 | — | Spring Cloud Gateway |
| tenant-service | 8100 | 9100 | System schema, no multi-tenancy |
| auth-service | 8101 | — | Keycloak Admin Client, no DB |
| student-service | 8102 | 9102 | |
| lead-service | 8104 | 9104 | Assignment notifications via RabbitMQ |
| course-service | 8106 | 9106 | Auto-subscription creation via gRPC |
| schedule-service | 8108 | 9108 | Room + Schedule + auto-lesson creation + student sync |
| payment-service | 8110 | 9110 | Subscription + PriceList + StudentPayment |
| finance-service | 8112 | 9112 | Transactions (INCOME/EXPENSE) + Payroll |
| analytics-service | 8114 | 9114 | NamedParameterJdbcTemplate, no JPA entities |
| notification-service | 8116 | — | RabbitMQ consumer, Email via Spring Mail, Broadcast |
| file-service | 8118 | — | MinIO, no DB |
| report-service | 8120 | — | PDF/Excel, no DB, calls analytics via gRPC |
| staff-service | 8122 | 9122 | Salary fields (salaryType, salaryPercentage) |
| task-service | 8124 | 9124 | Assignment notifications via RabbitMQ |
| lesson-service | 8126 | 9126 | Lesson + Attendance journal |
| settings-service | 8128 | 9128 | Tenant settings + catalogs (attendance/payment/roles/staff/finance) |
| audit-service | 8130 | — | MongoDB, RabbitMQ listener, no JPA/Redis/gRPC |

### Key Patterns

- **Entities** extend `BaseEntity` (UUID id, audit fields, optimistic locking via `@Version`)
- **DTOs** use Lombok `@Builder` + `@Data`. Mapper interfaces use MapStruct.
- **Controllers** return `ApiResponse<T>` wrapper. Access model is mixed: business CRUD increasingly uses `hasAuthority('PERMISSION_CODE')` alongside built-in roles, while tenant/platform administration (`/api/v1/tenants`, `/api/v1/auth/users`, `/api/v1/settings/roles`) stays role-only. `files` and `notifications` are intentional exceptions and are not fully permission-driven.
- **Granular permissions** — `SystemPermission` enum в common, `Permission` enum в settings-service (синхронизированы): `ROOMS_*`, `PRICE_LISTS_*`, `ANALYTICS_VIEW`, `LESSONS_*`, `GROUPS_*`, `SUBSCRIPTIONS_*`, `TASKS_*`, `STAFF_*`, `FINANCE_*`, `REPORTS_VIEW`, `SETTINGS_*`.
- **gRPC services** extend generated `*ImplBase`, annotated with `@GrpcService`. Convert between DTOs and protobuf manually in the gRPC service class.
- **API docs** available at `/swagger-ui.html` per service (SpringDoc OpenAPI).

## Tenant Service (port 8100)

System-level service that manages tenants (education centers). Key architectural points:

- **System schema only** — the `tenants` table lives in the `system` schema, NOT in per-tenant schemas. The service does NOT use schema-per-tenant multi-tenancy.
- **Multi-tenancy disabled** — `TenantServiceJpaConfig` overrides common's `TenantIdentifierResolver`/`TenantSchemaConnectionProvider` via a `@Primary HibernatePropertiesCustomizer` that removes the multi-tenant provider keys and forces `default_schema=system`.
- **Schema name format** — `schemaName = "tenant_" + tenant.getId().toString().replace("-", "")` (UUID without dashes, to fit length constraints). Generated after first save, then updated in a second save.
- **Schema creation** — calls `SELECT system.create_tenant_schema(:schemaName)` via `EntityManager` native query after setting the schema name.
- **Flyway** — runs in `system` schema (`flyway.schemas: system`, `flyway.default-schema: system`).
- **Endpoints**: `POST/GET/PUT/DELETE /api/v1/tenants`, `GET /api/v1/tenants/search?query=`
- **Delete guard** — tenant can only be deleted when `status = INACTIVE`; throws `BusinessException` otherwise.

## SaaS Subscription System (tenant-service + api-gateway)

### Планы и тарифы

Enum `TenantPlan`: `BASIC`, `EXTENDED`, `EXTENDED_PLUS` (старые `PROFESSIONAL`/`ENTERPRISE` удалены).

Цены (из `system.subscription_plans`, Flyway V18):
| План | Месяц | 6 мес (мес) | Год (мес) |
|---|---|---|---|
| BASIC | 20 000 тг | 18 000 тг (-10%) | 16 600 тг (-17%) |
| EXTENDED | 30 000 тг | 27 000 тг (-10%) | 24 900 тг (-17%) |
| EXTENDED_PLUS | 50 000 тг | 45 000 тг (-10%) | 41 500 тг (-17%) |

Enum `BillingPeriod`: `MONTHLY` (+30d), `SIX_MONTHS` (+183d), `ANNUAL` (+365d).

### Жизненный цикл тенанта с подпиской
```
Регистрация → TRIAL (7 дней) → [оплата] → ACTIVE → SUSPENDED/BANNED/INACTIVE
```
- При регистрации `RegistrationService` автоматически ставит `trialEndsAt = today + 7 days`
- `SubscriptionStatusDto.AccessState`: `TRIAL_ACTIVE`, `TRIAL_EXPIRED`, `SUBSCRIPTION_ACTIVE`, `SUBSCRIPTION_EXPIRED`, `SUSPENDED`, `BANNED`, `INACTIVE`

### Поля Tenant (добавлены V18)
`billingPeriod`, `subscriptionStartAt`, `subscriptionEndAt`, `subscriptionPrice`

### API Подписок
| Эндпоинт | Доступ | Описание |
|---|---|---|
| `GET /api/v1/subscription/plans` | Public | Список тарифных планов с ценами |
| `GET /api/v1/subscription/status` | Authenticated | Статус подписки текущего тенанта |
| `POST /api/v1/admin/tenants/{id}/subscription/activate` | SUPER_ADMIN | Активировать подписку тенанту |
| `GET /internal/tenants/{id}/subscription-status` | Internal | Для api-gateway filter (без JWT) |

### SubscriptionCheckFilter (api-gateway)
GlobalFilter с order=-50 (после SubdomainTenantFilter=-200, TenantHeaderFilter=-100).

**Логика**: Redis cache `sub-status:{tenantId}` TTL 5 мин → при промахе запрашивает `tenant-service/internal/...` → кэширует. При `TRIAL_ACTIVE` / `SUBSCRIPTION_ACTIVE` пропускает, иначе HTTP 402.

**Bypass paths** (не проверяются):
`/api/v1/register`, `/api/v1/subscription/**`, `/api/v1/auth/**`, `/auth/**`, `/actuator/**`, `/fallback/**`, `/swagger-ui`, `/v3/api-docs`, `/webjars/`, `/internal/`

**Fail-open**: если tenant-service недоступен — запрос пропускается (не блокируем из-за инфраструктурного сбоя).

**HTTP 402 error codes**: `TRIAL_EXPIRED`, `SUBSCRIPTION_EXPIRED`, `SUBSCRIPTION_SUSPENDED`, `TENANT_BANNED`, `SUBSCRIPTION_INACTIVE`.

**Evict cache**: при активации подписки `SubscriptionService.activate()` удаляет ключ `sub-status:{tenantId}` из Redis.

### Flyway V18 (tenant-service)
`V18__add_subscription_system.sql`:
- Обновляет CHECK constraint `plan` → BASIC/EXTENDED/EXTENDED_PLUS (мигрирует PROFESSIONAL→EXTENDED, ENTERPRISE→EXTENDED_PLUS)
- Добавляет поля `billing_period`, `subscription_start_at`, `subscription_end_at`, `subscription_price` в `system.tenants`
- Создаёт `system.subscription_plans` с seed-данными трёх тарифов
- Индексы на новые поля

### AdminDashboard / PlatformKPI — план distribution
`AdminDashboardResponse`: `basicPlanCount`, `extendedPlanCount`, `extendedPlusPlanCount`
`PlatformKpiResponse`: `basicCount`, `extendedCount`, `extendedPlusCount`
`ChurnAnalyticsResponse.byPlan`: ключи `BASIC`, `EXTENDED`, `EXTENDED_PLUS`

## Auth Service (port 8101)

Manages Keycloak users (staff members) via Keycloak Admin Client. No database, no JPA, no Redis, no RabbitMQ.

- **No persistence** — `DataSourceAutoConfiguration`, `HibernateJpaAutoConfiguration`, `FlywayAutoConfiguration`, `RedisAutoConfiguration`, `RabbitAutoConfiguration` are all excluded in `application.yml`.
- **Keycloak Admin Client** — `KeycloakConfig` creates a `Keycloak` bean using `client_credentials` grant against the `master` realm with a `crm-admin` service-account client.
- **Soft delete** — `DELETE /api/v1/auth/users/{id}` sets `enabled=false` in Keycloak, does NOT permanently delete.
- **Role management** — roles are assigned at realm level. On update, all existing realm roles are removed before assigning the new one.
- **Role filter** — `toDto()` strips Keycloak built-ins (`default-roles-*`, `offline_access`, `uma_authorization`) from the returned roles list.
- **Tenant isolation** — `GET /api/v1/auth/users` returns only users whose Keycloak `tenant_id` matches the current JWT `tenant_id`; `get/update/delete/reset-password/assign-permissions` are also blocked cross-tenant.
- **Create-user scoping** — when authenticated tenant context exists, `createUser()` uses the current JWT tenant and does not trust arbitrary `tenantId` from the request body for cross-tenant creation.
- **Tenant-filtered pagination** — `listUsers()` fetches Keycloak users in batches (`batchSize = max(size*4, 100)`) and filters by `tenant_id` attribute, because Keycloak API has no native tenant filter.
- **Custom roles** — `KeycloakRoleService` creates/updates/deletes tenant-scoped Keycloak roles. `KeycloakUserService` auto-applies role permissions from the role definition when creating/updating a user.
- **permissions_source** — attribute stored in Keycloak alongside `permissions`: `USER` = custom override, `ROLE:<keycloakRoleName>` = inherited from role template. Used for tracking permission origin.
- **Internal endpoints** — `PUT /internal/auth/roles/{name}` and `DELETE /internal/auth/roles/{name}` (called by settings-service, no JWT required — internal network only).
- **Secret** — `keycloak.client-secret` bound to env var `KEYCLOAK_CLIENT_SECRET` (default `change-me`).
- **Endpoints**: `POST/GET/PUT/DELETE /api/v1/auth/users`, `POST /api/v1/auth/users/{id}/reset-password`

### Known Issues & Solutions

#### nginx 502 после рестарта api-gateway
nginx кэширует IP контейнера — после рестарта api-gateway старый IP устаревает.

**Симптом**: все API-запросы возвращают 502 Bad Gateway после `make deploy-service s=api-gateway`.

**Решение**: `docker exec 1edu-nginx nginx -s reload` — перечитывает upstream IP из DNS.

`make deploy-service` вызывает `scripts/deploy.sh`, который делает reload автоматически. Проблема возникает только при ручном `docker compose up` без использования make.

#### OAuth URL "не доступен" (Google/Yandex backup) после рестартов
После рестарта `settings-service` в коротком окне возможны `503` на
`/api/v1/settings/google-drive-backup*` и `/api/v1/settings/yandex-disk-backup*`.

**Симптомы**:
- UI показывает "OAuth URL не доступен. Попробуйте позже.";
- в `api-gateway` логи `Gateway fallback: service=settings`.

**Решение**:
1. Дождаться `settings-service` health=UP и регистрации в Eureka.
2. Проверить `api-gateway /actuator/health` — `settings-service` должен быть в discovery list.
3. При необходимости перезапустить `api-gateway` (очистка stale routing cache).

#### ApiPay webhook 400 (signature missing/invalid)
ApiPay/Kaspi может прислать подпись не только в `X-Webhook-Signature`, но и в альтернативных заголовках.

**Симптомы**:
- `POST /internal/apipay/webhook` возвращает `400`;
- в логах `APIPAY_SIGNATURE_MISSING` или `APIPAY_SIGNATURE_INVALID`.

**Решение**:
- backend принимает `X-Webhook-Signature` (основной), а также `Signature`/`X-Signature`;
- поддерживает формат подписи `sha256=<hex>` и raw hex (`64` символа);
- для стабильности на стороне провайдера рекомендуется отправлять `X-Webhook-Signature: sha256=<hex>`.

#### ApiPay/KPAY: single vs generate endpoint
`/api/v1/payments/*/invoices/generate` — массовая генерация за месяц (по всем релевантным подпискам),
`/api/v1/payments/*/invoices/single` — создание счёта для одного ученика.

**Симптом**:
- ожидали 1 счёт, но создалось несколько.

**Решение**:
- для ручного счёта использовать только `.../invoices/single`;
- при нескольких абонементах у ученика передавать `subscriptionId` явно;
- payload для `.../invoices/generate` строгий (только `month`), лишние поля дают `400`.

#### MinIO internal URL в API-ответах (studentPhoto/logoUrl)
Если в БД хранится внутренний URL вида `http://minio:9000/...`, frontend не может загрузить изображение.

**Симптом**: в `students` / `settings` API приходит `studentPhoto`/`logoUrl` с хостом `minio:9000`, в браузере такие ссылки недоступны.

**Решение**:
1. В `.env` задать `MINIO_PUBLIC_URL=https://api.1edu.kz/minio`
2. В nginx добавить proxy для `/minio/* -> http://minio:9000/*`
3. В MinIO открыть anonymous download только для нужных префиксов:
  - `ondeedu-files/avatars`
  - `ondeedu-files/logos`

Backend уже нормализует legacy-URL в ответах (`student-service`, `student-search`, `settings-service`) на публичный base URL.

#### Flyway out-of-order — отсутствующие APIPAY/KPAY объекты в tenant-схемах
Если на проде уже применены более новые миграции (`V23+`), а `V21/V22` добавлены позже, они не применяются при `outOfOrder=false`.

**Симптомы**:
- `ERROR: column ... apipay_api_base_url does not exist` в `settings-service`
- `ERROR: relation apipay_invoices does not exist` / `kpay_invoices` в `payment-service`

**Решение**:
- миграция `V26__ensure_kpay_and_apipay_schema_backfill.sql` переопределяет `system.ensure_kpay_schema` и `system.ensure_apipay_schema` и прогоняет их по всем tenant-схемам;
- для применения достаточно `./deploy.sh restart tenant-service`.

#### Keycloak 26 User Profile — кастомные атрибуты (tenant_id, permissions)
Keycloak 26 использует **DeclarativeUserProfile** по умолчанию. Кастомные атрибуты, не объявленные в схеме User Profile, **молча игнорируются** при создании/обновлении пользователя через Admin API.

**Симптом**: `tenant_id` записывается без ошибок, но в JWT claim отсутствует (`null` / `None`).

**Решение**: в Keycloak Admin Console → `Realm settings → User profile → Create attribute` — добавить `tenant_id` (и любые другие кастомные атрибуты) перед использованием.

**Важно для новых инсталляций**: при поднятии нового Keycloak-реалма нужно вручную добавить атрибуты в User Profile Schema. `KeycloakSetupService` НЕ делает это автоматически — при необходимости добавить программное создание атрибутов через `keycloak.realm(realm).users().userProfile()`.

#### Keycloak — единый frontend client
Для web/login flow используется один client: **`1edu-web-app`**. Он должен:
- принимать обычный browser login (`standardFlowEnabled=true`)
- принимать password grant для backend-проверок и legacy login (`directAccessGrantsEnabled=true`)
- отдавать claims `tenant_id`, `permissions`, `roles` в JWT

`KeycloakSetupService` автоматически приводит `1edu-web-app` к нужному состоянию при старте `auth-service`: включает login settings и создаёт mappers `tenant_id-mapper`, `permissions-mapper`, `realm-access-roles`.

#### Keycloak URLs — разделяй internal и public
В production нужны две переменные:
```
KEYCLOAK_INTERNAL_URL=http://keycloak:8080/auth
KEYCLOAK_PUBLIC_URL=https://api.1edu.kz/auth
BASE_DOMAIN=1edu.kz
API_DOMAIN=api.1edu.kz
```
`KEYCLOAK_INTERNAL_URL` нужен для Admin Client и внутреннего `jwk-set-uri`, `KEYCLOAK_PUBLIC_URL` — для public issuer (`iss`) в JWT. Обе ссылки обязательно должны содержать суффикс `/auth`, иначе Admin Client получает HTTP 404 при `grantToken`, а resource servers валят валидацию токенов.

#### Public auth / registration flow
- Единственный публичный signup path: `POST /api/v1/register`
- Маршрут signup: `nginx /api/* -> api-gateway -> tenant-service`
- Публичные Keycloak/OIDC пути живут под `https://api.1edu.kz/auth/*`
- Browser login начинается с `/auth/realms/ondeedu/protocol/openid-connect/auth`
- Token endpoint: `/auth/realms/ondeedu/protocol/openid-connect/token`
- Отдельного backend login endpoint (`/api/v1/auth/login`) нет
- Realm импортируется с `"registrationAllowed": false`, поэтому Keycloak self-registration UI не используется

#### Monitoring stack
- Monitoring UI публикуются через nginx subpaths на `https://api.1edu.kz/*` и защищены Basic Auth
- Публичные ссылки: `/grafana/`, `/prometheus/`, `/zipkin/`, `/rabbitmq/`, `/eureka/`
- Container ports для Grafana, Prometheus, Zipkin, RabbitMQ, Eureka и Keycloak остаются привязаны к `127.0.0.1`
- Grafana provisioning использует стабильные datasource UID: `prometheus`, `zipkin`, `elasticsearch`
- Основные dashboards: `1edu CRM — Services Overview`, `1edu CRM — JVM Metrics`, `1edu CRM — Infrastructure`
- Alerting provisioned из файлов:
  - templates: `1edu-crm-templates`
  - rule groups: `1edu-crm-service-health`, `1edu-crm-performance`
- Для инфраструктурных метрик используются `postgres-exporter` и `redis-exporter`
- В deploy script добавлена очистка macOS metadata (`._*`, `.DS_Store`) в Grafana provisioning перед рестартом infra

#### API Gateway — LoadBalancer кэш и env-переменные
Spring Cloud LoadBalancer кэширует список инстансов из Eureka:
```yaml
spring.cloud.loadbalancer.cache.caffeine.spec: maximumSize=2048,expireAfterWrite=30s
```
Настраиваемые параметры через env:
| Env var | Default | Описание |
|---|---|---|
| `GATEWAY_TIMEOUT_DURATION` | `12s` | Таймаут Resilience4j TimeLimiter |
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | `0.1` | Zipkin sampling (в prod 10%, для debug 1.0) |
| `GATEWAY_LOG_LEVEL` | `INFO` | Лог Spring Cloud Gateway |
| `SPRING_SECURITY_LOG_LEVEL` | `WARN` | Лог Spring Security |

#### GlobalExceptionHandler — NoResourceFoundException
Добавлен обработчик `NoResourceFoundException` → возвращает `404 RESOURCE_NOT_FOUND` вместо `500`. Без него Spring Boot 3 возвращал 500 при запросе на несуществующий ресурс статики.

#### GlobalExceptionHandler — file upload / missing parts
Добавлены обработчики для ошибок при загрузке файлов, которые раньше давали 500 INTERNAL_ERROR:
- `MaxUploadSizeExceededException` → **413 PAYLOAD_TOO_LARGE** `FILE_TOO_LARGE`
- `MissingServletRequestPartException` → **400 MISSING_FILE** (когда part `file` не передан)
- `MissingServletRequestParameterException` → **400 MISSING_PARAMETER**

Эти исключения бросаются до входа в метод контроллера, поэтому try-catch в `FileService` их не ловил.
#### Docker — сеть при пересборке контейнера
При пересборке отдельного контейнера (`docker compose up -d --build auth-service`) новый контейнер подключается к **текущей** сети проекта (`1edu_crm_1edu-network`). Если старые сервисы работают на другой сети (например, `1edu_1edu-network` от предыдущего запуска), нужно явно подключить контейнер:
```bash
docker network connect --alias auth-service <old-network-name> <container-name>
```

#### Lombok в бизнес-сервисах
`compileOnly` зависимости не транзитивны — Lombok из `common` **не передаётся** в зависимые сервисы. Каждый бизнес-сервис должен явно объявлять в `build.gradle`:
```groovy
compileOnly 'org.projectlombok:lombok'
annotationProcessor 'org.projectlombok:lombok'
annotationProcessor 'org.mapstruct:mapstruct-processor:1.6.3'
annotationProcessor 'org.projectlombok:lombok-mapstruct-binding:0.2.0'
```

#### MapStruct + @Builder на Entity, наследующем BaseEntity
Lombok `@Builder` на Entity создаёт билдер только для собственных полей, без полей `BaseEntity` (`id`, `createdAt`, и т.д.). MapStruct при генерации кода пытается использовать builder и падает с ошибкой `Unknown property "id" in result type XxxBuilder`.

**Решение**: добавить `@BeanMapping(builder = @Builder(disableBuilder = true))` на метод маппера `toEntity(...)`, чтобы MapStruct использовал сеттеры вместо builder.

#### @ConditionalOnBean в common модуле (для сервисов без DataSource/Redis/RabbitMQ)
Сервисы, исключающие автоконфигурацию (`auth-service`, `file-service`), падают если `common` безусловно регистрирует бины, требующие инфраструктурных зависимостей.

**Применённые гарды:**
- `RabbitMQConfig` — `@ConditionalOnBean(ConnectionFactory.class)`
- `RedisConfig` — `@ConditionalOnBean(RedisConnectionFactory.class)`
- `TenantIdentifierResolver` — `@ConditionalOnBean(DataSource.class)`
- `TenantSchemaConnectionProvider` — `@ConditionalOnBean(DataSource.class)`
- `AuditLogPublisher` — `@ConditionalOnBean(RabbitTemplate.class)`
- `JpaConfig` — `@ConditionalOnBean(DataSource.class)`

**Правило**: любой `@Component` или `@Configuration` в `common`, принимающий бин инфраструктуры как зависимость, должен иметь соответствующий `@ConditionalOnBean`.

#### Flyway — несколько сервисов в одной схеме (system)
Сервисы `tenant`, `payment`, `lesson`, `settings`, `notification` все используют схему `system` для Flyway, что вызывает конфликты версий в общей таблице `flyway_schema_history`.

**Решение в `docker-compose.prod.yml`:**
```yaml
SPRING_FLYWAY_BASELINE_ON_MIGRATE: "true"   # для пустой схемы без истории
SPRING_FLYWAY_VALIDATE_ON_MIGRATE: "false"  # игнорировать checksum-конфликты между сервисами
```

#### Keycloak crm-admin service account — 403 при создании пользователей
При первом деплое `crm-admin` service account не имеет прав manage-users в Keycloak — `KeycloakSetupService` получает 403 везде, регистрация возвращает `USER_CREATION_FAILED`.

**Причина**: realm импортируется без service account roles для crm-admin client.

**Решение**: через Keycloak Admin API назначить service account нужные роли из `realm-management`:
```bash
# 1. Получить admin token
ADMIN_TOKEN=$(curl -s -X POST http://keycloak:8080/auth/realms/master/protocol/openid-connect/token \
  -d "grant_type=password&client_id=admin-cli&username=$KC_ADMIN&password=$KC_PASS" | jq -r .access_token)
# 2. crm-admin client ID
CLIENT_ID=$(curl -s "http://keycloak:8080/auth/admin/realms/ondeedu/clients?clientId=crm-admin" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.[0].id')
# 3. service account user ID
SA_ID=$(curl -s "http://keycloak:8080/auth/admin/realms/ondeedu/clients/$CLIENT_ID/service-account-user" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r .id)
# 4. realm-management client ID
RM_ID=$(curl -s "http://keycloak:8080/auth/admin/realms/ondeedu/clients?clientId=realm-management" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.[0].id')
# 5. Assign roles: manage-users, manage-clients, manage-realm, view-users, view-clients, view-realm, manage-identity-providers
```
Роли: `manage-users`, `manage-clients`, `manage-realm`, `view-users`, `view-clients`, `view-realm`, `manage-identity-providers`.

После назначения ролей — перезапустить auth-service (KeycloakSetupService отработает повторно и настроит mappers).

#### JVM heap vs container limit — medium/small сервисы лезут в swap
Medium сервисы с `Xmx=384m` в контейнере 512MB уходят в swap: JVM heap + metaspace (~100m) + CodeCache (~50m) = ~534MB > 512MB. Это даёт P99 latency 3-5 сек и 835MB swap usage.

**Решение** — снизить Xmx в `.env`:
```
JVM_XMS_MEDIUM=128m   # было 192m
JVM_XMX_MEDIUM=256m   # было 384m — ключевое изменение
JVM_XMS_SMALL=96m     # было 128m
JVM_XMX_SMALL=192m    # было 256m
```
После изменения — rolling restart сервисов с `docker compose up -d --force-recreate --no-deps <service>`.
Результат: swap 835MB → 130MB, P99 < 50ms.

#### RabbitMQ management API — path prefix `/rabbitmq`
`infrastructure/rabbitmq/rabbitmq.conf` устанавливает `management.path_prefix = /rabbitmq`. Это значит management HTTP API доступен по пути `/rabbitmq/api/...`, а не `/api/...`.

**Симптом**: `scripts/ensure-rabbitmq-topology.sh` возвращает `curl: (22) 404 Not Found` при попытке создать exchanges/queues.

**Решение**: в `ensure-rabbitmq-topology.sh` `RABBITMQ_API_BASE` должен содержать prefix:
```bash
RABBITMQ_API_BASE="http://${RABBITMQ_API_HOST}:${RABBITMQ_API_PORT}/rabbitmq/api"
```
Уже исправлено в репо. Применять при написании новых скриптов, работающих с RabbitMQ Management API.

#### PgBouncer — query_wait_timeout при одновременном старте сервисов
При старте 10+ сервисов одновременно PgBouncer отдаёт `FATAL: query_wait_timeout` — пул исчерпывается, клиенты ждут дольше 120 с (дефолтный `query_wait_timeout`) и падают.

**Симптом**: `org.postgresql.util.PSQLException: FATAL: query_wait_timeout` во время Flyway-миграции при старте сервиса.

**Решение**:
1. `DEFAULT_POOL_SIZE` в `docker-compose.prod.yml` — **100** (было 20, потом 50 — недостаточно при 15+ сервисах с min_idle=2-5). Обновлено.
2. `HIKARI_CONNECTION_TIMEOUT_MS` в `.env` — **30000** (было 3000). Хватает времени дождаться свободного слота.
3. При первом деплое поднимать сервисы группами с задержкой 60 с между группами — не все сразу.

**Почему 100**: 15 сервисов × min_idle=2 = 30 соединений в idle, плюс пики при старте дают 60-80. `default_pool_size=50` переполняется.

#### Config Server — сервисы без Spring Cloud Config
Сервисы (`audit-service`, `file-service`) с `spring-cloud-starter-config` на classpath падают при старте если Config Server недоступен.

**Решение** — добавить в `environment` контейнера:
```yaml
SPRING_CLOUD_CONFIG_ENABLED: "false"
SPRING_CLOUD_CONFIG_IMPORT_CHECK_ENABLED: "false"
```

#### Analytics Flyway V2 — кросс-сервисные таблицы
Миграция V2 для analytics-service создаёт индексы на таблицах других сервисов (`transactions`, `subscriptions`, `lessons`), которых может не быть в текущей схеме.

**Решение**: оборачивать каждый DDL в `DO $$ BEGIN IF EXISTS (SELECT 1 FROM information_schema.tables WHERE ...) THEN ... END IF; END $$;`

При ошибке миграции — удалить failed-запись вручную:
```sql
DELETE FROM <schema>.flyway_schema_history WHERE version = '2' AND success = false;
```

### Security

OAuth2 Resource Server with Keycloak (realm: `ondeedu`). JWT roles are extracted from both `realm_access.roles` and `resource_access.*.roles` via `KeycloakRealmRoleConverter` and prefixed with `ROLE_`.

### Tech Stack

Spring Boot 3.4.2, Spring Cloud 2024.0.0, gRPC 1.68.2, Protobuf 4.29.3, PostgreSQL 16, Redis 7, RabbitMQ 3.13, Elasticsearch 8.17, Keycloak 26, MinIO (S3-compatible), Flyway for migrations, Lombok, MapStruct 1.6.3, Resilience4j, Micrometer + Zipkin + Prometheus + Grafana for observability.

## Analytics Service (port 8114)

Сервис аналитики — читает данные из shared PostgreSQL БД (multi-tenant schema) через **NamedParameterJdbcTemplate** (нативный SQL). Не использует JPA-сущности для чтения аналитики, только для Flyway-миграций.

### Дашборды и эндпоинты
| Эндпоинт | Описание |
|---|---|
| `GET /api/v1/analytics/dashboard` | Дашборд руководителя (посещаемость, загрузка групп, конверсии, финансы, топ-сотрудник, клиенты) |
| `GET /api/v1/analytics/finance-report` | Финансовый отчёт (доходы, расходы, прибыль, по статьям/источникам/группам, сверка с абонементами) |
| `GET /api/v1/analytics/subscriptions` | Отчёт по абонементам (с поиском подозрительных — `?onlySuspicious=true`) |
| `GET /api/v1/analytics/funnel` | Воронка продаж по этапам лидов |
| `GET /api/v1/analytics/lead-conversions` | Конверсии лидов, ARPU/ARPPU, пробные занятия |
| `GET /api/v1/analytics/managers` | Эффективность менеджеров (FRT p50/p75/p90, конверсия) |
| `GET /api/v1/analytics/teachers` | Аналитика преподавателей (выручка, загрузка, удержание, индекс) |
| `GET /api/v1/analytics/retention` | Когортный анализ удержания (M+0..M+5) |
| `GET /api/v1/analytics/group-load` | Загрузка групп (текущий снимок) |
| `GET /api/v1/analytics/room-load` | Загрузка аудиторий + таймлайн на дату |
| `GET /api/v1/analytics/group-attendance/{groupId}` | Посещаемость группы по месяцам |

### Таблицы (добавлены Flyway V1)
- `lessons` — занятия (group_id, teacher_id, room_id, lesson_type: GROUP/INDIVIDUAL/TRIAL)
- `attendances` — посещаемость (lesson_id, student_id, status: ATTENDED/ABSENT/PLANNED)
- `subscriptions` — абонементы (student_id, group_id, total_lessons, lessons_left)
- `services` — индивидуальные услуги
- `rooms` — аудитории
- `lead_activities` — история действий по лиду (для расчёта FRT)
- `student_groups` — записи студентов в группы (enrolled_at, completed_at)
- `transactions` — переименованы из `payments` (type: INCOME/EXPENSE)

### Паттерн подозрительных абонементов
Флаг `suspicious = true` если `(total_lessons - lessons_left) != attendance_count`.

### Главный дашборд — статистика за сегодня
| Эндпоинт | Описание |
|---|---|
| `GET /api/v1/analytics/today?date=YYYY-MM-DD` | Сводка за дату (по умолчанию сегодня): доходы/расходы, новые абонементы, занятия, посещения, записи, непродлённые абонементы, задолженности, неоплаченные посещения, дни рождения |

Разделы ответа `TodayStatsResponse`:
- **Сводка**: `todayRevenue`, `todayExpenses`, `newSubscriptions`, `conductedLessons`, `attendedStudents`, `newEnrollments`
- **Непродлённые абонементы** (`ExpiredSubscriptionDto`): `expiredByDate` (заканчиваются в течение 7 дней), `expiredByRemaining` (осталось ≤2 занятия), `overdue` (просроченные)
- **Задолженности** (`DebtorDto`): студенты с отрицательным балансом (оплата < стоимость абонементов)
- **Неоплаченные посещения** (`UnpaidVisitDto`): посещения за последние 30 дней без активного абонемента с остатком
- **Дни рождения** (`BirthdayDto`): ближайшие 7 дней с `daysUntil` и `turnsAge`

### Redis кэширование (AnalyticsCacheConfig)

`AnalyticsCacheConfig` объявляет `@Primary CacheManager`, переопределяя бин из `common`. Включает как общие кэши (`tenants`, `students`, `courses`), так и все аналитические.

**TTL стратегия:**
| TTL | Кэши |
|---|---|
| 5 мин | `analytics:today` |
| 10 мин | `analytics:dashboard`, `analytics:group-load`, `analytics:room-load`, `analytics:subscriptions` |
| 15 мин | `analytics:finance`, `analytics:funnel`, `analytics:lead-conversions`, `analytics:managers`, `analytics:teachers`, `analytics:group-attendance` |
| 30 мин | `analytics:retention` |

**Tenant-изоляция ключей:** `TenantCacheKeyGenerator` (`@Component("tenantCacheKeyGenerator")`) добавляет `tenantId` в начало каждого ключа кэша — предотвращает утечку данных между тенантами. Все `@Cacheable` используют `keyGenerator = "tenantCacheKeyGenerator"`.

Добавленные в V2 индексы: `TO_CHAR(birth_date, 'MM-DD')` для запросов дней рождения, составные индексы по `transactions(date,type,status)`, `subscriptions(status,start_date)`, `lessons(date,status)`.

## Settings Service (port 8128)

Сервис настроек — управляет оперативными настройками тенанта (одна запись на тенанта).

### API
| Эндпоинт | Метод | Доступ | Описание |
|---|---|---|---|
| `/api/v1/settings` | GET | `TENANT_ADMIN`, `MANAGER`, `RECEPTIONIST`, `TEACHER` или `SETTINGS_VIEW` | Получить настройки текущего тенанта (или дефолтные если не настроены) |
| `/api/v1/settings` | PUT | `TENANT_ADMIN` или `SETTINGS_EDIT` | Обновить (или создать) настройки тенанта (upsert) |
| `/api/v1/settings/logo` | POST | `TENANT_ADMIN` или `SETTINGS_EDIT` | Загрузить логотип тенанта и обновить `logo_url` |

### Таблица `tenant_settings` (Flyway V1, расширена V13/V15)
Одна строка на тенант-схему. Поля:
- **Общие**: `timezone` (Asia/Tashkent), `currency` (UZS), `language` (ru)
- **Рабочие часы**: `working_hours_start` (09:00), `working_hours_end` (21:00)
- **Рабочие дни**: `working_days` (JSON строка, дефолт: Пн–Сб)
- **Занятия**: `default_lesson_duration_min` (60), `trial_lesson_duration_min` (45), `max_group_size` (20), `slot_duration_min`
- **Посещаемость**: `auto_mark_attendance` (false), `attendance_window_days` (7)
- **Уведомления**: `sms_enabled` (false), `email_enabled` (true), `sms_sender_name`
- **Финансы**: `late_payment_reminder_days` (3), `subscription_expiry_reminder_days` (3)
- **UI**: `brand_color` (#4CAF50), `logo_url`
- **Профиль центра** (V13): `center_name`, `city`, `work_phone`, `address`, `director_name`, `director_basis`, `corporate_email`, `bank_account`, `bank`, `bin`, `bik`, `requisites`, `branch_count`, `main_direction`

### Каталоги настроек (V13/V15 — pre-configured catalogs)

Системные справочники с дефолтными значениями, которые можно настраивать под тенанта:

| Таблица | Описание | Дефолтные значения |
|---|---|---|
| `attendance_status_configs` | Статусы посещаемости с флагами | 6 системных: Посетил, Пропустил, Болел, Отпуск, Посетил авто, Разовый урок |
| `payment_sources` | Источники оплаты | 5 вариантов: Безналичный, Интернет эквайринг, Наличные, Карта/терминал, Kaspi QR |
| `role_configs` | Tenant-scoped custom roles, синкаемые в реальные Keycloak realm roles | Built-in системные роли не хранятся и не редактируются тут |
| `staff_status_configs` | Статусы сотрудников | Кастомные статусы для UЦ |
| `finance_category_configs` | Категории доходов/расходов | 7 дефолтных: 3 дохода (Абонементы, Услуги, Прочие), 4 расхода (Зарплаты, Аренда, Маркетинг, Коммунальные) |

Флаги `attendance_status_configs`:
- `deduct_lesson` — списывать занятие с абонемента
- `require_payment` — требует оплаты
- `count_as_attended` — считается посещением

### API (расширенный)
| Эндпоинт | Метод | Доступ | Описание |
|---|---|---|---|
| `/api/v1/settings` | GET | Роли + `SETTINGS_VIEW` | Получить настройки (или дефолтные) |
| `/api/v1/settings` | PUT | TENANT_ADMIN / `SETTINGS_EDIT` | Upsert настроек (включая logo_url) |
| `/api/v1/settings/logo` | POST | TENANT_ADMIN / `SETTINGS_EDIT` | Загрузить логотип в MinIO и обновить `logo_url` |
| `/api/v1/settings/finance-categories` | GET/POST | TENANT_ADMIN / accountant / finance/settings permissions | Категории финансов |
| `/api/v1/settings/finance-categories/{id}` | PUT/DELETE | TENANT_ADMIN / accountant / finance/settings permissions | Обновление/удаление категории |
| `/api/v1/settings/staff-statuses` | GET | TENANT_ADMIN / manager / `SETTINGS_VIEW` / `STAFF_VIEW` | Список кастомных статусов сотрудников |
| `/api/v1/settings/staff-statuses` | POST | TENANT_ADMIN / `SETTINGS_EDIT` / `STAFF_EDIT` | Создание кастомного статуса сотрудника |
| `/api/v1/settings/staff-statuses/{id}` | PUT/DELETE | TENANT_ADMIN / `SETTINGS_EDIT` / `STAFF_EDIT` | Обновление/удаление кастомного статуса |

### Logo Upload
`FileServiceClient` в settings-service — REST-клиент для загрузки логотипа в MinIO через `/api/v1/files/upload`. `upsertSettings()` обрабатывает файл и сохраняет URL в `logo_url`.

### Поведение upsert
`SettingsService.upsertSettings()` использует `findAll().stream().findFirst()` — если записи нет, создаёт новую с дефолтными значениями, затем применяет патч через MapStruct (`NullValuePropertyMappingStrategy.IGNORE` — null поля в запросе не перезаписывают).

### GET без записи
`getSettings()` возвращает `TenantSettings.builder().build()` (дефолтные значения) без сохранения в БД — позволяет получить конфигурацию до первой явной настройки.

### Redis кэширование
Кэш `settings` с ключом через `TenantCacheKeys.fixed('tenant-settings')`. `@CacheEvict` при каждом PUT.

### Logo Upload — лимит файлов
`application.yml` settings-service:
```yaml
spring.servlet.multipart.max-file-size: 10MB
spring.servlet.multipart.max-request-size: 15MB
```
**Симптом `MaxUploadSizeExceededException` (500)**: Spring дефолт 1MB. Уже исправлено — лимит 10MB.

### Кастомные роли — синхронизация с Keycloak
`RoleConfigService` при create/update/delete вызывает `AuthRoleClient` (REST → `PUT/DELETE /internal/auth/roles/{name}`), который через `KeycloakRoleService` создаёт/обновляет/удаляет роль в Keycloak.

**Ограничения:**
- Системные роли (`TENANT_ADMIN`, `MANAGER`, `TEACHER`, `RECEPTIONIST`, `ACCOUNTANT`, `SUPER_ADMIN`) защищены от изменения — выбрасывают `SYSTEM_ROLE_CONFIG_FORBIDDEN`
- Переименование ролей не поддерживается (`ROLE_RENAME_NOT_SUPPORTED`) — нужно создать новую роль и переназначить пользователей
- Имя роли нормализуется автоматически через `RoleNameUtils.normalizeRoleName()` — lowercase/пробелы/дефисы принимаются, конвертируются в UPPER_CASE_SNAKE. **`@Pattern` в `SaveRoleConfigRequest` удалена** — валидация на уровне util, а не DTO.
- Удаление роли блокируется если она назначена пользователям (`ROLE_IN_USE`)

**Именование в Keycloak:**
- Системные роли: `TENANT_ADMIN`, `MANAGER` (без префикса)
- Кастомные роли: `TENANT_{SANITIZED_TENANT_ID}__{ROLE_NAME}` (пример: `TENANT_ABC123__CURATOR`)
- `RoleNameUtils.toKeycloakRoleName(tenantId, roleName)` — преобразование
- `RoleNameUtils.toDisplayRoleName(tenantId, keycloakRoleName)` — обратное (для ответа API)

## Payment Service — Student Payments (port 8110)

### Учёт фактических платежей студентов
Модуль `StudentPayment` добавлен в payment-service (V2 Flyway). Хранит каждый фактический взнос студента в разрезе месяца и подписки.

### Расчёт ежемесячного взноса
```
months_duration = CEIL(priceList.validityDays / 30.0)   # из PriceList
monthly_expected = subscription.amount / months_duration
```
Если PriceList не привязан — по разнице `start_date` → `end_date`.

### Таблица `student_payments` (V2)
| Поле | Тип | Описание |
|---|---|---|
| student_id | UUID | Студент |
| subscription_id | UUID | Подписка (FK) |
| amount | NUMERIC | Сумма этого платежа |
| paid_at | DATE | Дата фактической оплаты |
| payment_month | VARCHAR(7) | Месяц за который платёж ('YYYY-MM') |
| method | VARCHAR(20) | CASH / CARD / TRANSFER / OTHER |

### API Student Payments
| Эндпоинт | Доступ | Описание |
|---|---|---|
| `POST /api/v1/payments/student-payments` | TENANT_ADMIN / FINANCE_CREATE | Записать платёж |
| `GET /api/v1/payments/student-payments/student/{id}` | TENANT_ADMIN / FINANCE_VIEW | История студента (подписки + помесячно) |
| `GET /api/v1/payments/student-payments/overview?month=YYYY-MM` | TENANT_ADMIN / FINANCE_VIEW | Месячный отчёт: PAID/PARTIAL/UNPAID по всем студентам |
| `GET /api/v1/payments/student-payments/debtors` | TENANT_ADMIN / FINANCE_VIEW | Должники с суммой и количеством месяцев долга |
| `DELETE /api/v1/payments/student-payments/{id}` | TENANT_ADMIN / FINANCE_EDIT | Удалить запись (исправление ошибки) |

### Статусы месяца
- **PAID** — paid >= expected
- **PARTIAL** — 0 < paid < expected
- **UNPAID** — paid == 0

## Audit Service (port 8130)

Сервис аудита — хранит все события платформы в MongoDB. Не использует JPA, Flyway, Redis, gRPC.

### Ключевые решения
- **`@SpringBootApplication(exclude = {DataSourceAutoConfiguration, HibernateJpaAutoConfiguration, FlywayAutoConfiguration, RedisAutoConfiguration, ...})`** — обязательно для сервисов без RDBMS/Redis, использующих `common` (который объявляет эти бины).
- **`grpc.server.port: -1`** — отключает gRPC сервер в REST-only сервисах.
- **`SystemAuditEvent` / `TenantAuditEvent`** — должны иметь `@NoArgsConstructor` + `@AllArgsConstructor` для корректной десериализации Jackson при получении из RabbitMQ.
- **MongoDB TTL**: `@Indexed(expireAfterSeconds = 31_536_000)` на поле `timestamp` — автоматическое удаление старых записей (SystemAuditLog: 1 год, TenantAuditLog: 90 дней).

### Коллекции MongoDB
| Коллекция | TTL | Описание |
|---|---|---|
| `system_audit_logs` | 365 дней | Действия SUPER_ADMIN (бан тенантов, смена плана) |
| `tenant_audit_logs` | 90 дней | Действия внутри УЦ (студенты, оплаты, занятия) |

### API
| Эндпоинт | Доступ | Описание |
|---|---|---|
| `GET /api/v1/audit/system` | SUPER_ADMIN | Системный лог (action, targetId, actorId, from/to, page/size) |
| `GET /api/v1/audit/tenant` | TENANT_ADMIN / SUPER_ADMIN | Лог тенанта (category, action, actorId, from/to). SUPER_ADMIN должен передавать `X-Tenant-ID`. |

### Паттерн публикации событий (AuditLogPublisher)
`AuditLogPublisher` (`@Component` в `common`) — fire-and-forget публикация через RabbitMQ. Инжектируется в любой сервис. Ошибки публикации логируются, но **не блокируют** основной поток.

## SUPER_ADMIN — Управление тенантами

### Жизненный цикл тенанта
```
TRIAL → ACTIVE → SUSPENDED → INACTIVE (soft delete)
                  ↓
               BANNED
```

### Ban / Unban
- `POST /api/v1/admin/tenants/{id}/ban` — устанавливает `status=BANNED`, записывает `bannedAt`, `bannedReason`, `bannedUntil`.
- `POST /api/v1/admin/tenants/{id}/unban` — возвращает `status=ACTIVE`, очищает ban-поля.

### Soft Delete / Restore
- `DELETE /api/v1/admin/tenants/{id}` — устанавливает `deletedAt=now()`, `status=INACTIVE`. `@SQLRestriction("deleted_at IS NULL")` автоматически скрывает запись от обычных JPA-запросов.
- `POST /api/v1/admin/tenants/{id}/restore` — очищает `deletedAt`, возвращает `status=ACTIVE`. Использует `findByIdIncludingDeleted()` (native query, bypasses `@SQLRestriction`).

### Hard Delete
- `DELETE /api/v1/admin/tenants/{id}/permanent` — удаляет запись из БД + `DROP SCHEMA IF EXISTS <schemaName> CASCADE`.
- **SQL injection protection**: перед DROP проверяется `schemaName.matches("^[a-zA-Z0-9_]+$")`.

### Flyway миграция V2 (tenant-service)
`V2__add_ban_and_soft_delete.sql` — добавляет поля `banned_at`, `banned_reason`, `banned_until`, `deleted_at` в `system.tenants`, обновляет CHECK constraint для статуса `BANNED`.

## Observability Stack

### Доступ к инструментам
| Инструмент | URL | Назначение |
|---|---|---|
| Grafana | http://localhost:3000 | Дашборды (логин из .env `GF_SECURITY_ADMIN_*`) |
| Prometheus | http://localhost:9090 | Метрики и алерты |
| Zipkin | http://localhost:9411 | Distributed tracing |
| RabbitMQ UI | http://localhost:15672 | Очереди и consumers |
| Kibana | http://localhost:5601 | Логи (если настроен) |

### Архитектура observability
- **Metrics**: Micrometer → `/actuator/prometheus` → Prometheus (scrape 15s) → Grafana
- **Tracing**: Spring Cloud Sleuth → Zipkin (storage: Elasticsearch, индекс `zipkin`)
- **RabbitMQ metrics**: built-in `rabbitmq_prometheus` plugin → порт 15692 → Prometheus
- **PostgreSQL metrics**: `postgres-exporter` → порт 9187 → Prometheus
- **Redis metrics**: `redis_exporter` → порт 9121 → Prometheus

### Grafana дашборды (auto-provisioned)
Файлы в `infrastructure/grafana/provisioning/dashboards/`:
| Файл | Дашборд | Что показывает |
|---|---|---|
| `01-services-overview.json` | Services Overview | req/s, latency p50/p95/p99, error rate по сервисам |
| `02-jvm-metrics.json` | JVM Metrics | heap, threads, GC, HikariCP connection pool |
| `03-infrastructure.json` | Infrastructure | PostgreSQL, Redis, RabbitMQ метрики |

### Prometheus scrape targets
Все сервисы scrape по именам Docker-контейнеров (`service-name:port/actuator/prometheus`).
**Порты** — точно по port map из CLAUDE.md.

### Zipkin — persistent storage
Zipkin использует Elasticsearch для хранения трейсов (`ES_HOSTS=http://elasticsearch:9200`, индекс `zipkin`).
Данные не теряются при перезапуске контейнера.

### RabbitMQ Prometheus Plugin
Файл `infrastructure/rabbitmq/enabled_plugins` монтируется в контейнер и включает `rabbitmq_prometheus`.
Метрики доступны на порту **15692**.

### Правила при добавлении нового сервиса
1. Добавить job в `infrastructure/prometheus/prometheus.yml` (имя контейнера + правильный порт)
2. Убедиться что в `application.yml` есть `management.endpoints.web.exposure.include: prometheus`
3. Добавить панель в `01-services-overview.json` если нужен мониторинг HTTP

## Performance & High-Load Optimizations

### Database Indexes (V16 — tenant-service migration)
`V16__add_performance_indexes.sql` создаёт функцию `system.add_performance_indexes(schema)` и применяет её ко всем существующим схемам.

Покрытые таблицы и индексы:
| Таблица | Индексы |
|---|---|
| `students` | `phone`, `email` (partial), `status`, `created_at`, GIN trgm на `first_name`/`last_name` |
| `leads` | `phone`, `stage`, `assigned_to`, `created_at` |
| `staff` | `role`, `status` |
| `courses` | `teacher_id`, `status` |
| `schedules` | `course_id`, `teacher_id`, `status` |
| `tasks` | `assigned_to`, `status`, `due_date` |
| `transactions` | `(type, status, transaction_date)` составной, `student_id`, `transaction_date` |
| `subscriptions` | `student_id`, `status`, `(student_id, status)` составной, `end_date` |
| `student_payments` | `student_id`, `subscription_id`, `payment_month` |
| `lessons` | `group_id`, `teacher_id`, `(lesson_date, status)` составной |
| `attendances` | `lesson_id`, `student_id`, `status` |
| `student_groups` | `student_id`, `group_id`, `status` |

**Правило для новых таблиц**: при добавлении таблицы в `create_tenant_schema()` — сразу добавлять индексы в `add_performance_indexes()` и `add_extended_indexes()`.

### Database Indexes (V17 — расширенные индексы)
`V17__add_extended_indexes.sql` создаёт `system.add_extended_indexes(schema)` для новых query patterns.

| Таблица | Новые индексы |
|---|---|
| `lessons` | `room_id` (room load analytics), `room_id+lesson_date` (timeline), `lesson_type`, partial `PLANNED` lessons |
| `transactions` | `staff_id`, `salary_month`, `(staff_id, salary_month)` (payroll) |
| `subscriptions` | `start_date`, `group_id`, partial `ACTIVE` на `(student_id, end_date)` |
| `student_groups` | `enrolled_at`, `completed_at`, `(student_id, group_id, status)` composite |
| `attendances` | `(student_id, status)`, partial `ATTENDED` на `lesson_id` |
| `schedules` | `(start_date, end_date)` WHERE `ACTIVE` |
| Settings catalogs | `system_status` (attendance), `active+sort` (payment_sources, staff_statuses, finance_categories) |
| `notification_logs` | `event_type`, `reference_id`, `(recipient_user_id, created_at)` |

`TenantService.createTenant()` вызывает `add_performance_indexes` + `add_extended_indexes` при создании новых тенантов (обе функции применяются автоматически).

### Tracing & Logging
Все сервисы: `probability: 0.1` (10% семплинг), `com.ondeedu: INFO`, `org.hibernate.SQL: WARN`.
**Для отладки локально** — временно поменять на `probability: 1.0` и `DEBUG`.

### RabbitMQ Consumer Concurrency
Добавлено во все сервисы с RabbitMQ:
```yaml
spring.rabbitmq.listener.simple:
  concurrency: 5        # min threads
  max-concurrency: 10   # max threads under load
  prefetch: 20          # сообщений за раз на consumer
```
Notification-service: `concurrency: 10`, `max-concurrency: 20`, `prefetch: 30` — высокий приоритет доставки уведомлений.

### Async Email
`EmailService.sendEmail()` аннотирован `@Async` — SMTP-вызов не блокирует RabbitMQ listener thread. `@EnableAsync` добавлен в `NotificationServiceApplication`. Ошибки SMTP логируются без реброска исключения (fire-and-forget).

### Admin Dashboard — Cache + Parallel
`AdminDashboardService.getDashboard()`:
- `@Cacheable(value = "admin:dashboard")` — TTL 5 мин, не гоняем 7×N SQL запросов при каждом вызове
- `parallelStream()` для `buildStatsWithDb()` — параллельный сбор статистики по схемам

`SuperAdminAnalyticsService.getPlatformKpis()`:
- `@Cacheable(value = "admin:platform-kpis")` — TTL 10 мин
- `parallelStream()` + thread-safe `DoubleAdder`/`AtomicLong` — параллельный агрегат по схемам

`SuperAdminAnalyticsService.getRevenueTrend()`:
- `@Cacheable(value = "admin:revenue-trend")` — TTL 15 мин
- `parallelStream()` + `synchronized` на записях monthData map

### Redis Cache TTL Strategy (common RedisConfig)
| TTL | Кэши |
|---|---|
| 5 мин | `leads`, `admin:dashboard` |
| 10 мин | `subscriptions`, `admin:platform-kpis`, `lessons` |
| 15 мин | `admin:revenue-trend`, `schedules`, `role-configs` |
| 30 мин | `staff`, `settings`, `rooms`, `payment-sources`, `attendance-statuses`, `finance-categories`, `staff-statuses` |
| 1 час | `tenants`, `price_lists`, `price-lists` |
| 2 часа | `courses`, `role-permissions` |

### TenantCacheKeys — изолированные ключи кэша
`TenantCacheKeys` (`common/cache/`) — утилитарный класс для формирования tenant-изолированных ключей:
```java
TenantCacheKeys.fixed("all")          // → "<tenantId>::all"
TenantCacheKeys.id(entityId)          // → "<tenantId>::<id>"
```
Использовать в `key =` параметрах `@Cacheable`/`@CacheEvict` вместо голых строк. Без этого данные тенантов смешивались бы в одном ключе. Не путать с `TenantCacheKeyGenerator` (bean для `keyGenerator =` параметра — оба подхода корректны).

### SettingsService — оптимизированный запрос
`SettingsRepository.findFirstBy()` вместо `findAll().stream().findFirst()` — возвращает только одну строку из БД (LIMIT 1).

### HikariCP Pool Sizes
| Сервис | max-pool-size | min-idle |
|---|---|---|
| Business services (default) | 10 | 2 |
| notification-service | 15 | 5 |
| tenant-service | 15 | 5 |
| settings-service | 10 | 3 |

### Latency Percentiles (P99/P95/P50) — histogram buckets
Для работы `histogram_quantile` в Grafana у каждого сервиса должны быть включены histogram buckets:
```yaml
# В x-spring-env docker-compose.prod.yml (применяется ко всем сервисам)
MANAGEMENT_METRICS_DISTRIBUTION_PERCENTILES-HISTOGRAM_HTTP_SERVER_REQUESTS: "true"
MANAGEMENT_METRICS_DISTRIBUTION_SLO_HTTP_SERVER_REQUESTS: 50ms,100ms,200ms,500ms,1s,2s,5s
MANAGEMENT_METRICS_DISTRIBUTION_PERCENTILES-HISTOGRAM_HIKARICP_CONNECTIONS_ACQUIRE: "true"
```
Без этого Prometheus не экспортирует `http_server_requests_seconds_bucket` → P99 панель и HikariCP Acquire Time в Grafana показывают "No data".

### Prometheus Scrape Strategy
- **api-gateway**: 15s (точная видимость латентности и ошибок)
- **Все остальные сервисы**: 30s (global, вдвое меньше CPU нагрузки от metric collection)
- **scrape_timeout**: 10s (явно, было 10s implicit)
- Scraping `/actuator/prometheus` вызывает кратковременный CPU spike (JMX metric collection) — 30s интервал снижает это вдвое

### JVM GC Tuning (Dockerfile.prebuilt)
Все сервисы запускаются с G1GC флагами:
- `-XX:+UseG1GC` — явно включить G1 (auto в Java 21, но явно = предсказуемо)
- `-XX:MaxGCPauseMillis=200` — цель паузы ≤200ms
- `-XX:G1HeapRegionSize=4m` — регион 4MB для heap <4GB (оптимально)
- `-XX:ReservedCodeCacheSize=256m` — **критично для api-gateway**: без этого JIT компилятор переполняет CodeCache → major GC 5-6ms спайк (наблюдался на графиках Grafana)
- `-XX:+UseCodeCacheFlushing` — автофлаш CodeCache при заполнении
- `-XX:+ExitOnOutOfMemoryError` — restart контейнера при OOM вместо zombie-процесса

### Nginx Load Balancer (upstream api_gateway)
Nginx использует `upstream api_gateway` с keepalive пулом на api-gateway:
```nginx
upstream api_gateway {
    server api-gateway:8090;
    keepalive 64;            # переиспользование соединений
    keepalive_requests 1000;
    keepalive_timeout  60s;
}
```
`proxy_next_upstream` retry на `error timeout 502 503 504` (только GET/HEAD).

## SUPER_ADMIN Analytics (tenant-service)
| Эндпоинт | Описание |
|---|---|
| `GET /api/v1/admin/analytics/platform` | Platform KPIs: MRR/ARR, ARPU, active rate, trial conversion, students/staff totals |
| `GET /api/v1/admin/analytics/revenue-trend?months=12` | Помесячная выручка по всем тенантам |
| `GET /api/v1/admin/analytics/tenant-growth?months=12` | Рост/отток тенантов по месяцам |
| `GET /api/v1/admin/analytics/churn` | Churn rate за 30/90 дней, разбивка по планам |

## SUPER_ADMIN Full Control Endpoints (tenant-service)

Полный список новых эндпоинтов для управления и мониторинга — все требуют роль `SUPER_ADMIN`:

### Управление тенантами
| Эндпоинт | Описание |
|---|---|
| `GET /api/v1/admin/tenants/search?q=&status=&page=&size=` | Поиск тенантов по имени/email с пагинацией |
| `GET /api/v1/admin/tenants/expiring-trials?days=7` | Список тенантов с истекающим trial-периодом |
| `GET /api/v1/admin/tenants/quota-warnings?threshold=80` | Тенанты, использующие ≥ порога % лимита студентов/сотрудников |
| `GET /api/v1/admin/tenants/banned` | Список забаненных тенантов |
| `GET /api/v1/admin/tenants/deleted` | Список soft-deleted тенантов |

### Управление конкретным тенантом
| Эндпоинт | Описание |
|---|---|
| `PATCH /api/v1/admin/tenants/{id}/trial` | Продлить trial-период (body: `{ trialEndsAt, reason }`) |
| `PATCH /api/v1/admin/tenants/{id}/notes` | Обновить внутренние заметки (не видны тенанту) |
| `GET /api/v1/admin/tenants/{id}/overview` | Полный cross-tenant обзор: студенты, сотрудники, финансы, активность |
| `PATCH /api/v1/admin/tenants/{id}/status` | Сменить статус тенанта |
| `PATCH /api/v1/admin/tenants/{id}/plan` | Сменить план тенанта + лимиты |
| `POST /api/v1/admin/tenants/{id}/ban` | Забанить тенанта |
| `POST /api/v1/admin/tenants/{id}/unban` | Разбанить тенанта |
| `DELETE /api/v1/admin/tenants/{id}` | Soft-delete тенанта |
| `POST /api/v1/admin/tenants/{id}/restore` | Восстановить soft-deleted тенанта |
| `DELETE /api/v1/admin/tenants/{id}/permanent` | Permanent delete (DROP SCHEMA CASCADE) |

### Bulk операции
| Эндпоинт | Описание |
|---|---|
| `POST /api/v1/admin/tenants/bulk-status` | Массовая смена статуса (max 50 тенантов, body: `{ tenantIds, status, reason }`) |

### Cross-tenant deep dive (`TenantOverviewResponse`)
`GET /api/v1/admin/tenants/{id}/overview` возвращает:
- **Студенты**: total, active, trial, inactive, newThisMonth, expiringSubscriptions
- **Сотрудники**: total, staffByRole map
- **Финансы**: revenueThisMonth, revenueLastMonth, expensesThisMonth, profitThisMonth, revenueTotal, debtorsCount, totalDebt
- **Занятия**: lessonsThisMonth, planned, completed, cancelled
- **Абонементы**: active, expired
- **Последняя активность**: recentTransactions (10), recentEnrollments (10), recentLessons (10)

### Компоненты
- `TenantAdminService` — все новые операции, нативные SQL-запросы по схемам
- `TenantOverviewResponse` — полный DTO с cross-tenant данными
- `QuotaWarningDto` — предупреждение о превышении квоты (`studentsUsagePct`, `staffUsagePct`, `criticalStudents`)
- `ExtendTrialRequest` — `trialEndsAt: LocalDate, reason: String`
- `BulkStatusRequest` — `tenantIds: List<UUID>, status: TenantStatus, reason: String`

## PgBouncer — Connection Pooling

PgBouncer добавлен перед PostgreSQL как прокси пула соединений.

### Конфигурация
- Image: `edoburu/pgbouncer:latest`
- Порт: `127.0.0.1:6432 -> pgbouncer:5432`
- `auth_type = scram-sha-256` — обязательно для PostgreSQL 16+ с SCRAM passwords
- Режим: **session** — обязателен для Hibernate multi-tenancy (`SET search_path`)
- `server_reset_query = RESET ALL` — очищает `search_path` при возврате соединения в пул
- `max_client_conn = 500` — принимает до 500 HikariCP подключений
- `default_pool_size = 50` — реальных Postgres соединений на (user, database). **Важно:** значение 20 слишком мало при одновременном старте 15+ сервисов — вызывает `query_wait_timeout`. Установлено 50 в `docker-compose.prod.yml`.
- `max_db_connections = 150` — жёсткий лимит реальных соединений к Postgres (PostgreSQL `max_connections=200`, оставляем 50 для Keycloak, pg_exporter, ad-hoc)

### Маршрутизация
- Spring Boot сервисы: `jdbc:postgresql://pgbouncer:5432/${DB_NAME}` (через PgBouncer)
- Keycloak: `jdbc:postgresql://postgres:5432/${DB_NAME}` (напрямую — JPA без multi-tenancy)
- postgres-exporter: `postgres:5432` (напрямую — мониторинг)

### Зависимости в docker-compose
`x-depends-infra` ждёт `pgbouncer: service_healthy` вместо `postgres: service_healthy`. PgBouncer сам ждёт postgres.

### Мониторинг PgBouncer
```bash
psql -h localhost -p 6432 -U ondeedu pgbouncer -c "SHOW POOLS;"
psql -h localhost -p 6432 -U ondeedu pgbouncer -c "SHOW STATS;"
psql -h localhost -p 6432 -U ondeedu pgbouncer -c "SHOW SERVERS;"
```

### Важно при session-mode
Session-mode не даёт прироста throughput для нагрузок с короткими транзакциями (в отличие от transaction-mode). Выгода — **мультиплексирование соединений**: 20 сервисов × 10 HikariCP = 200 потенциальных соединений → PgBouncer сводит к 50 реальным Postgres соединениям, снижая нагрузку на PostgreSQL connection overhead (~5 МБ RAM каждое).

## Grafana — Admin Platform Dashboard

Добавлен дашборд `04-admin-platform.json` ("1edu CRM — Admin Platform Health") для SUPER_ADMIN.

### Секции дашборда
- **Platform Overview**: сервисы up/down, total req/s, error rate %, P99 latency api-gateway, DB pool active/max, Redis hit rate
- **Per-Service Health**: статус всех сервисов (таблица), requests/s bargauge
- **Error Rates & Latency**: error rate per service (timeseries), P99 latency per service
- **Database & Connection Pools**: HikariCP active connections per service, pending threads (pool exhaustion risk), Postgres stats (active connections, TPS, cache hit rate, slow queries)
- **Message Queues**: RabbitMQ queue depth per queue, publish/deliver rate, DLQ depth
- **JVM Health**: heap used % per service, GC pause P99 per service

UID: `1edu-admin-platform`, ссылки на остальные дашборды в links секции.

## Finance Service — Payroll (port 8112)

Модуль расчёта зарплат добавлен в finance-service (Flyway V12).

### Модели расчёта зарплаты
- **FIXED** — фиксированная сумма в месяц
- **PER_STUDENT_PERCENTAGE** — процент от суммы абонементов студентов в группе

Enum `SalaryType` (FIXED, PER_STUDENT_PERCENTAGE) находится в `com.ondeedu.common.payroll` (common-модуль).

### Изменения схемы (V12)
- `staff` таблица: добавлены `salary_type`, `salary_percentage`
- `transactions` таблица: добавлены `staff_id`, `salary_month` (VARCHAR(7), формат `YYYY-MM`)
- Индексы по `staff_id` и `salary_month`

### Ключевые компоненты
- `SalaryService` — расчёт зарплат через нативный SQL (не JPA) для производительности на больших объёмах
- `SalaryQueryRepository` — нативные SQL-запросы для вычислений в разрезе сотрудников
- `SalarySchemaResolver` — разрешает имена тенант-схем для кросс-тенантных зарплатных запросов (паттерн для других аналогичных задач)

### API Payroll
| Эндпоинт | Доступ | Описание |
|---|---|---|
| `GET /api/v1/finance/salary?month=YYYY-MM&year=YYYY` | TENANT_ADMIN | Обзор зарплат за месяц по всем сотрудникам |
| `GET /api/v1/finance/salary/staff/{staffId}` | TENANT_ADMIN | История зарплат конкретного сотрудника |
| `POST /api/v1/finance/salary/payments` | TENANT_ADMIN | Записать выплату зарплаты |

## Staff Service — Salary Fields (port 8122)

Добавлены поля для расчёта зарплаты (V12, через gRPC связь с finance-service):
- `salaryType` (Enum: FIXED / PER_STUDENT_PERCENTAGE)
- `salaryPercentage` (Decimal — процент для PER_STUDENT_PERCENTAGE модели)
- `customStatus` (String — тенант-специфичный статус сотрудника из `staff_status_configs`)

gRPC proto обновлён: `staff.proto` включает salary-поля в `StaffResponse`.

## Schedule Service — Auto-Lesson & Student Sync (port 8108)

### Автосоздание занятий из расписания
При создании расписания автоматически генерируются занятия (lessons) для каждого дня в диапазоне дат. При обновлении расписания — синхронизируются изменения. При удалении — занятия помечаются удалёнными.

**Компоненты:**
- `LessonGrpcClient` — gRPC-клиент для lesson-service (порт 9126)
- `ScheduleService.synchronizeLessonsOnCreate()` — генерация занятий по дням недели
- `ScheduleService.synchronizeLessonsOnUpdate()` — синхронизация при изменениях
- `ScheduleService.synchronizeLessonsOnDelete()` — мягкое удаление занятий

### Синхронизация студентов курса в группы расписания
При привязке расписания к курсу студенты курса автоматически записываются в группу расписания.

**Новые сущности (V10):**
- `CourseStudentLink` — связь студентов с курсами (`course_id`, `student_id`)
- `ScheduleStudentEnrollment` — запись студента в группу расписания (`student_id`, `group_id`, `status`, `enrolled_at`, `completed_at`, `notes`)

**Таблицы (V10):**
- `course_students` — backfilled при миграции
- `student_groups` — backfilled при миграции
- Индексы по `course_id`, `student_id`, `group_id`

### Автоподстановка учителя и лимита из курса
При создании/обновлении расписания с `courseId` — `teacherId` и `maxStudents` автоматически берутся из курса через gRPC и **не могут быть изменены вручную** (ошибка `COURSE_BOUND_FIELD_IMMUTABLE`).

**Компоненты:**
- `CourseGrpcClient` — gRPC-клиент для course-service (порт 9106); метод `getCourseInfo(courseId)` возвращает `CourseInfo(teacherId, enrollmentLimit, roomId)`
- `course.proto` обновлён: `Course` message содержит `teacher_id` (field 13) и `room_id` (field 14)
- `CourseGrpcService.toGrpcCourse()` обновлён: заполняет `teacherId` и `roomId`
- `docker-compose.prod.yml`: `GRPC_CLIENT_COURSE_SERVICE_ADDRESS: discovery:///course-service` для schedule-service

### Проверка вместимости аудитории
При создании/обновлении расписания с `roomId`:
- **Блокировка** (`ROOM_CAPACITY_EXCEEDED`): если `maxStudents > room.capacity`
- **Уведомление** via RabbitMQ (`NOTIFICATION_EXCHANGE` / `notification.assignment`): если `maxStudents == room.capacity` — событие `ROOM_AT_CAPACITY` отправляется создателю расписания

## Course Service — Auto-Subscription (port 8106)

При добавлении студента на курс автоматически создаётся подписка (subscription) через gRPC.

**Компоненты:**
- `PaymentGrpcClient` — gRPC-клиент для payment-service (порт 9110)
- `PaymentGrpcService.createSubscriptionForCourse()` — новый RPC-метод в payment-service
- `payment.proto` обновлён с новыми message-типами для auto-subscription

Логика: подписка создаётся с `end_date` = `start_date` + длительность курса; цена берётся из payment-service.

## Notification Service — Broadcast & Assignment (port 8116)

### Broadcast уведомления
Рассылка по всем тенантам или конкретному тенанту (только SUPER_ADMIN).

| Эндпоинт | Доступ | Описание |
|---|---|---|
| `POST /api/v1/notifications/broadcast` | SUPER_ADMIN | Отправить broadcast (email/push) по тенантам |

**`BroadcastNotificationRequest`**: `tenantId` (null = все тенанты), `subject`, `body`, `type`.

### Assignment уведомления
При назначении лида или задачи сотруднику отправляется уведомление через RabbitMQ (fire-and-forget).

**Новые компоненты:**
- `AssignmentNotificationEvent` — событие назначения (в common-модуле, extends `BaseEntity`)
- `LeadAssignmentNotificationService` — публикует событие при назначении лида
- `TaskAssignmentNotificationService` — публикует событие при назначении задачи
- `StaffGrpcClient` — резолвит данные сотрудника в lead/task сервисах

**RabbitMQ (новые):**
- Routing key: `notification.assignment`
- Queue: `notification.assignment.queue`

### Обновление `notification_logs` (V14)
Добавлены колонки для трекинга событий: `event_type`, `reference_type`, `reference_id` — позволяет связать уведомление с конкретной сущностью (лид, задача, назначение).

### Важно: memory budget
Notification-service требует увеличенного memory budget в docker-compose — настроено в `docker-compose.prod.yml`.

### Email через Brevo SMTP
`EmailService` отправляет письма через JavaMailSender. SMTP-вызов — `@Async` (не блокирует listener thread).

Настройка через env-переменные:
```
SMTP_HOST=smtp-relay.brevo.com
SMTP_PORT=587
SMTP_USERNAME=<brevo_login>
SMTP_PASSWORD=<brevo_smtp_key>
MAIL_FROM=noreply@1edu.kz      # fallback → SMTP_USERNAME
MAIL_REPLY_TO=support@1edu.kz  # опционально
```

`EmailNotificationEvent` (в common-модуле) — dedicated event для прямой доставки email через `notification.exchange` / `notification.email.queue`. Поля: `recipientEmail`, `subject`, `body`.

### SaaS lifecycle emails (tenant-service)
`TenantSubscriptionNotificationService` отправляет три типа писем владельцам тенантов:

| Событие | Триггер | `eventType` |
|---|---|---|
| Welcome | Сразу после регистрации | `tenant.registered.welcome` |
| Payment due | За 1 день до `subscriptionEndAt` | `tenant.subscription.payment_due.<date>` |
| Payment overdue | На следующий день после `subscriptionEndAt` | `tenant.subscription.payment_overdue.<date>` |
| Trial expired | После истечения `trialEndsAt` | `tenant.trial.expired.<date>` |

- **De-dup**: проверяется `system.notification_logs` перед отправкой — повторная отправка пропускается если `eventType + tenantId` уже `SENT`
- **Scheduler**: `TenantSubscriptionNotificationScheduler` — hourly cron (настраивается `TENANT_SUBSCRIPTION_NOTIFICATIONS_CRON`), отключается `TENANT_SUBSCRIPTION_NOTIFICATIONS_ENABLED=false`
- **Timezone**: дата сравнивается в timezone тенанта (`tenant_settings.timezone`), fallback UTC
- **`@EnableScheduling` + `@EnableAsync`** на `TenantServiceApplication`

## Backend Updates (2026-04-11)

### Lesson Service — attendance business rules
- `AttendanceService` теперь применяет окно редактирования посещаемости из `tenant_settings.attendance_window_days`.
  - Ошибка: `ATTENDANCE_EDIT_WINDOW_EXPIRED`
  - Поведение: изменение посещаемости запрещено для уроков старше configured window.
  - Fix off-by-one: `attendance_window_days=1` разрешает редактирование только «сегодня», со следующего дня изменения блокируются.
- Любые attendance-операции (`mark`, `bulk`, `mark-all`) теперь блокируют неактивных студентов.
  - Ошибка: `STUDENT_NOT_ACTIVE`
- Для `GET /api/v1/lessons/{lessonId}/attendance` список теперь формируется из:
  - уже сохранённых записей `attendances`
  - активных записей `student_groups` на дату урока
  - активных `course_students` (через `lesson.service_id` и fallback через `schedules.course_id`) на дату урока
- Это закрывает кейс: новый студент, добавленный в уже существующий курс, отображается в attendance-листе без ручного пересохранения расписания.
- Дефолтный статус в attendance-листе для ещё не сохранённых записей теперь учитывает `tenant_settings.auto_mark_attendance`:
  - `true` → `ATTENDED`
  - `false` → `PLANNED`
- При 3-м пропуске (`ABSENT`) за неделю (Mon-Sun) публикуется IN_APP событие:
  - `eventType = STUDENT_ABSENCE_3X_WEEK`
  - exchange/key: `notification.exchange` / `notification.assignment`
  - tenant-level просмотр события доступен ролям `MANAGER` и `RECEPTIONIST` (и `SUPER_ADMIN`), но не как общий поток для `TENANT_ADMIN`

### Schedule Service — tenant settings constraints enforcement
- `ScheduleService` на `create/update` теперь применяет ограничения из `tenant_settings`:
  - рабочие часы (`working_hours_start`, `working_hours_end`) → `SCHEDULE_OUTSIDE_WORKING_HOURS`
  - валидный диапазон времени (`end > start`) → `INVALID_SCHEDULE_TIME_RANGE`
  - кратность длительности слоту (`slot_duration_min`) → `SCHEDULE_SLOT_DURATION_VIOLATION`
  - допустимые дни недели (`working_days`) → `SCHEDULE_OUTSIDE_WORKING_DAYS`
  - лимит размера группы (`max_group_size`) → `SCHEDULE_MAX_GROUP_SIZE_EXCEEDED`
- Добавлена валидация активности преподавателя при создании/изменении расписания:
  - `staff.status` должен быть `ACTIVE`
  - ошибка: `SCHEDULE_TEACHER_NOT_ACTIVE`
- Добавлена проверка конфликта кабинета по времени:
  - для `ACTIVE` расписаний в одном `room_id` нельзя иметь пересечение по диапазону дат + `daysOfWeek` + интервалу `startTime..endTime`
  - ошибка: `SCHEDULE_ROOM_TIME_CONFLICT`

### Auth Service — staff linkage for account provisioning
- В `CreateUserRequest`/`UpdateUserRequest` добавлено поле `staffId` (optional).
- `KeycloakUserService` сохраняет связку в Keycloak attribute `staff_id` и возвращает её в `UserDto.staffId`.
- Это поддерживает frontend flow «выбрать сотрудника → создать логин/пароль/роль» без отдельного endpoint.

### API Gateway — fallback observability
- `FallbackController` теперь пишет `WARN` лог на каждый fallback с полями:
  - `service`
  - `tenant` (из `X-Tenant-ID`)
  - `method`
  - `path`
- Это упрощает диагностику кейсов `"<Service> service is temporarily unavailable"`.

### Course Service — constraints and active-status checks
- В `CourseService` добавлена валидация ограничения группы через tenant settings:
  - `enrollmentLimit <= tenant_settings.max_group_size`
  - Ошибка: `COURSE_MAX_GROUP_SIZE_EXCEEDED`
- Валидация количества назначенных студентов:
  - `studentIds.size() <= enrollmentLimit` (если enrollmentLimit задан)
  - Ошибка: `COURSE_ENROLLMENT_LIMIT_EXCEEDED`
- Валидация активности участников перед назначением:
  - преподаватель должен быть `staff.status = ACTIVE` (`COURSE_TEACHER_NOT_ACTIVE`)
  - студенты должны быть `students.status = ACTIVE` (`COURSE_STUDENT_NOT_ACTIVE`)
- Валидация применяется в `createCourse` и `updateCourse`; для студентов — на добавляемом подмножестве.

### Finance + Payment — amount change reason codes
- В `transactions` и `student_payments` добавлены поля:
  - `amount_change_reason_code`
  - `amount_change_reason_other`
- Для `FinanceService.updateTransaction()` при изменении `amount` теперь обязателен `amountChangeReasonCode`:
  - `TRANSACTION_AMOUNT_REASON_REQUIRED`
- Общие правила валидации reason-полей:
  - `...ReasonCode=OTHER` требует `...ReasonOther` (`*_OTHER_REQUIRED`)
  - `...ReasonOther` запрещён для кодов кроме `OTHER` (`*_OTHER_FORBIDDEN`)
- В `StudentPaymentService.recordPayment()` поддержаны reason-поля с той же логикой `OTHER`.
- Новая миграция `tenant-service` `V19__ensure_amount_change_reason_schema.sql`:
  - добавляет поля во все существующие tenant-схемы;
  - обновляет CHECK constraints по допустимым кодам;
  - для новых тенантов вызывается `system.ensure_amount_change_reason_schema(:schemaName)` в `TenantService.createTenant()`.
