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
| lead-service | 8104 | 9104 | |
| course-service | 8106 | 9106 | |
| schedule-service | 8108 | 9108 | Room + Schedule entities |
| payment-service | 8110 | 9110 | Subscription + PriceList + StudentPayment |
| finance-service | 8112 | 9112 | Transactions (INCOME/EXPENSE) |
| analytics-service | 8114 | 9114 | NamedParameterJdbcTemplate, no JPA entities |
| notification-service | 8116 | — | RabbitMQ consumer, Email via Spring Mail |
| file-service | 8118 | — | MinIO, no DB |
| report-service | 8120 | — | PDF/Excel, no DB, calls analytics via gRPC |
| staff-service | 8122 | 9122 | |
| task-service | 8124 | 9124 | |
| lesson-service | 8126 | 9126 | Lesson + Attendance journal |
| settings-service | 8128 | 9128 | Tenant settings (upsert, one row per tenant) |
| audit-service | 8130 | — | MongoDB, RabbitMQ listener, no JPA/Redis/gRPC |

### Key Patterns

- **Entities** extend `BaseEntity` (UUID id, audit fields, optimistic locking via `@Version`)
- **DTOs** use Lombok `@Builder` + `@Data`. Mapper interfaces use MapStruct.
- **Controllers** return `ApiResponse<T>` wrapper. Use `@PreAuthorize` for role-based access (roles: `TENANT_ADMIN`, `MANAGER`, `RECEPTIONIST`, `TEACHER`).
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

## Auth Service (port 8101)

Manages Keycloak users (staff members) via Keycloak Admin Client. No database, no JPA, no Redis, no RabbitMQ.

- **No persistence** — `DataSourceAutoConfiguration`, `HibernateJpaAutoConfiguration`, `FlywayAutoConfiguration`, `RedisAutoConfiguration`, `RabbitAutoConfiguration` are all excluded in `application.yml`.
- **Keycloak Admin Client** — `KeycloakConfig` creates a `Keycloak` bean using `client_credentials` grant against the `master` realm with a `crm-admin` service-account client.
- **Soft delete** — `DELETE /api/v1/auth/users/{id}` sets `enabled=false` in Keycloak, does NOT permanently delete.
- **Role management** — roles are assigned at realm level. On update, all existing realm roles are removed before assigning the new one.
- **Role filter** — `toDto()` strips Keycloak built-ins (`default-roles-*`, `offline_access`, `uma_authorization`) from the returned roles list.
- **Secret** — `keycloak.client-secret` bound to env var `KEYCLOAK_CLIENT_SECRET` (default `change-me`).
- **Endpoints**: `POST/GET/PUT/DELETE /api/v1/auth/users`, `POST /api/v1/auth/users/{id}/reset-password`

### Known Issues & Solutions

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
| `/api/v1/settings` | GET | Все роли | Получить настройки текущего тенанта (или дефолтные если не настроены) |
| `/api/v1/settings` | PUT | TENANT_ADMIN | Обновить (или создать) настройки тенанта (upsert) |

### Таблица `tenant_settings` (Flyway V1)
Одна строка на тенант-схему. Поля:
- **Общие**: `timezone` (Asia/Tashkent), `currency` (UZS), `language` (ru)
- **Рабочие часы**: `working_hours_start` (09:00), `working_hours_end` (21:00)
- **Рабочие дни**: `working_days` (JSON строка, дефолт: Пн–Сб)
- **Занятия**: `default_lesson_duration_min` (60), `trial_lesson_duration_min` (45), `max_group_size` (20)
- **Посещаемость**: `auto_mark_attendance` (false), `attendance_window_days` (7)
- **Уведомления**: `sms_enabled` (false), `email_enabled` (true), `sms_sender_name`
- **Финансы**: `late_payment_reminder_days` (3), `subscription_expiry_reminder_days` (3)
- **UI**: `brand_color` (#4CAF50)

### Поведение upsert
`SettingsService.upsertSettings()` использует `findAll().stream().findFirst()` — если записи нет, создаёт новую с дефолтными значениями, затем применяет патч через MapStruct (`NullValuePropertyMappingStrategy.IGNORE` — null поля в запросе не перезаписывают).

### GET без записи
`getSettings()` возвращает `TenantSettings.builder().build()` (дефолтные значения) без сохранения в БД — позволяет получить конфигурацию до первой явной настройки.

### Redis кэширование
Кэш `settings` с ключом `tenant-settings`. `@CacheEvict` при каждом PUT. Tenant-изоляция обеспечивается через `TenantCacheKeyGenerator` из `common`.

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
| `DELETE /api/v1/payments/student-payments/{id}` | TENANT_ADMIN | Удалить запись (исправление ошибки) |

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

## SUPER_ADMIN Analytics (tenant-service)
| Эндпоинт | Описание |
|---|---|
| `GET /api/v1/admin/analytics/platform` | Platform KPIs: MRR/ARR, ARPU, active rate, trial conversion, students/staff totals |
| `GET /api/v1/admin/analytics/revenue-trend?months=12` | Помесячная выручка по всем тенантам |
| `GET /api/v1/admin/analytics/tenant-growth?months=12` | Рост/отток тенантов по месяцам |
| `GET /api/v1/admin/analytics/churn` | Churn rate за 30/90 дней, разбивка по планам |
