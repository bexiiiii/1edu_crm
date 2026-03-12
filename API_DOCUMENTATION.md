# 1edu CRM — Полная API Документация для Фронтенда

## Содержание

1. [Общая информация](#1-общая-информация)
2. [Аутентификация](#2-аутентификация)
3. [Общие форматы](#3-общие-форматы)
4. [Роли и права доступа](#4-роли-и-права-доступа)
5. [Регистрация УЦ (публичный)](#5-регистрация-уц-публичный)
6. [Tenant Service (8100)](#6-tenant-service-8100)
7. [Auth Service (8101)](#7-auth-service-8101)
8. [Student Service (8102)](#8-student-service-8102)
9. [Lead Service (8104)](#9-lead-service-8104)
10. [Course Service (8106)](#10-course-service-8106)
11. [Schedule Service (8108)](#11-schedule-service-8108)
12. [Payment Service (8110)](#12-payment-service-8110)
13. [Finance Service (8112)](#13-finance-service-8112)
14. [Analytics Service (8114)](#14-analytics-service-8114)
15. [Notification Service (8116)](#15-notification-service-8116)
16. [File Service (8118)](#16-file-service-8118)
17. [Report Service (8120)](#17-report-service-8120)
18. [Staff Service (8122)](#18-staff-service-8122)
19. [Task Service (8124)](#19-task-service-8124)
20. [Lesson Service (8126)](#20-lesson-service-8126)
21. [Settings Service (8128)](#21-settings-service-8128)
22. [Audit Service (8130)](#22-audit-service-8130)
23. [Справочник Enum-ов](#23-справочник-enum-ов)

---

## 1. Общая информация

### Базовый URL

| Среда | API Base URL | Keycloak |
|-------|-------------|----------|
| **Production (сервер)** | `https://beta.1edu.kz` | `https://beta.1edu.kz/auth/` |
| **Local (локальный бекенд)** | `http://localhost:8090` | `http://localhost:8080/auth/` |

> Для локальной разработки фронта с сервером — используй **`https://beta.1edu.kz`**.

Все эндпоинты имеют префикс `/api/v1/`.

### Порты сервисов (прямой доступ)

| Сервис | HTTP | gRPC |
|--------|------|------|
| tenant-service | 8100 | 9100 |
| auth-service | 8101 | — |
| student-service | 8102 | 9102 |
| lead-service | 8104 | 9104 |
| course-service | 8106 | 9106 |
| schedule-service | 8108 | 9108 |
| payment-service | 8110 | 9110 |
| finance-service | 8112 | 9112 |
| analytics-service | 8114 | 9114 |
| notification-service | 8116 | — |
| file-service | 8118 | — |
| report-service | 8120 | — |
| staff-service | 8122 | 9122 |
| task-service | 8124 | 9124 |
| lesson-service | 8126 | 9126 |
| settings-service | 8128 | 9128 |
| audit-service | 8130 | — |

### Маршрутная таблица API Gateway (`localhost:8090`)

Все запросы с фронтенда идут через единый gateway. CORS открыт (`allowedOriginPatterns: *`).

| Путь | Сервис |
|------|--------|
| `/api/v1/students/**` | student-service |
| `/api/v1/courses/**`, `/api/v1/groups/**` | course-service |
| `/api/v1/leads/**` | lead-service |
| `/api/v1/payments/**`, `/api/v1/subscriptions/**`, `/api/v1/price-lists/**` | payment-service |
| `/api/v1/schedules/**`, `/api/v1/rooms/**` | schedule-service |
| `/api/v1/lessons/**`, `/api/v1/attendance/**` | lesson-service |
| `/api/v1/settings/**` | settings-service |
| `/api/v1/audit/**` | audit-service |
| `/api/v1/staff/**` | staff-service |
| `/api/v1/finance/**` | finance-service |
| `/api/v1/tasks/**` | task-service |
| `/api/v1/analytics/**` | analytics-service |
| `/api/v1/reports/**` | report-service |
| `/api/v1/auth/**` | auth-service |
| `/api/v1/tenants/**`, `/api/v1/admin/**` | tenant-service |
| `/api/v1/notifications/**` | notification-service |
| `/api/v1/files/**` | file-service |

---

## 2. Аутентификация

Система использует **OAuth2 / Keycloak JWT токены**.

### Заголовки запроса

```http
Authorization: Bearer <access_token>
Content-Type: application/json
X-Tenant-ID: <tenant_uuid>
```

> `X-Tenant-ID` — UUID тенанта. Для обычных пользователей берётся из JWT claim `tenant_id` автоматически бэкендом. Передавай явно только если у тебя несколько тенантов (SUPER_ADMIN) или для явного указания контекста.

### Как получить токен

Через Keycloak (realm: `ondeedu`):

**Production (сервер):**
```http
POST https://beta.1edu.kz/auth/realms/ondeedu/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password&client_id=1edu-web-app&username=<login>&password=<pass>
```

**Local:**
```http
POST http://localhost:8080/auth/realms/ondeedu/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password&client_id=1edu-web-app&username=<login>&password=<pass>
```

> **Важно**: Keycloak развёрнут с context path `/auth`. URL токена всегда включает `/auth/realms/...`.

### Структура JWT (полезные claims)

```json
{
  "sub": "user-uuid",
  "tenant_id": "tenant-uuid",
  "roles": ["TENANT_ADMIN"],
  "permissions": ["STUDENTS_VIEW", "LESSONS_CREATE"],
  "realm_access": {
    "roles": ["TENANT_ADMIN", "offline_access", "uma_authorization"]
  }
}
```

> `tenant_id` — UUID тенанта, передаётся в заголовке `X-Tenant-ID` при запросах.
> `roles` — роль пользователя (через `realm-roles` mapper в `1edu-web-app-dedicated` scope).
> `permissions` — гранулярные права (через `permissions-mapper`).
> `realm_access.roles` — также содержит роли, доступен для обратной совместимости.

---

## 3. Общие форматы

### Ответ API — `ApiResponse<T>`

Все эндпоинты возвращают обёртку:

```json
{
  "success": true,
  "message": "OK",
  "errorCode": null,
  "data": { ... },
  "timestamp": "2026-01-01T10:00:00Z"
}
```

При ошибке:
```json
{
  "success": false,
  "message": "Student not found",
  "errorCode": "NOT_FOUND",
  "data": null,
  "timestamp": "2026-01-01T10:00:00Z"
}
```

### Пагинированный ответ — `PageResponse<T>`

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8,
  "first": true,
  "last": false,
  "hasNext": true,
  "hasPrevious": false
}
```

### Параметры пагинации (query params)

| Параметр | Тип | По умолчанию | Описание |
|----------|-----|-------------|----------|
| `page` | int | 0 | Номер страницы (0-based) |
| `size` | int | 20 | Размер страницы |
| `sort` | string | — | Поле и направление (e.g. `createdAt,desc`) |

---

## 4. Роли и права доступа

### Системные роли

| Роль | Описание |
|------|----------|
| `SUPER_ADMIN` | Управление платформой, все тенанты |
| `TENANT_ADMIN` | Полный доступ внутри своего тенанта |
| `MANAGER` | Менеджер учебного центра |
| `RECEPTIONIST` | Ресепшионист |
| `TEACHER` | Преподаватель |

### Гранулярные permissions (настраиваются в Settings Service)

| Permission | Описание |
|-----------|----------|
| `STUDENTS_VIEW` | Просмотр студентов |
| `STUDENTS_CREATE` | Создание студентов |
| `STUDENTS_EDIT` | Редактирование студентов |
| `STUDENTS_DELETE` | Удаление студентов |
| `LEADS_VIEW` | Просмотр лидов |
| `LEADS_CREATE` | Создание лидов |
| `LEADS_EDIT` | Редактирование лидов |
| `LEADS_DELETE` | Удаление лидов |
| `GROUPS_VIEW` | Просмотр групп/курсов |
| `GROUPS_CREATE` | Создание групп/курсов |
| `GROUPS_EDIT` | Редактирование групп/курсов |
| `GROUPS_DELETE` | Удаление групп/курсов |
| `STAFF_VIEW` | Просмотр сотрудников |
| `STAFF_CREATE` | Создание сотрудников |
| `STAFF_EDIT` | Редактирование сотрудников |
| `STAFF_DELETE` | Удаление сотрудников |
| `LESSONS_VIEW` | Просмотр занятий |
| `LESSONS_CREATE` | Создание занятий |
| `LESSONS_EDIT` | Редактирование занятий |
| `LESSONS_DELETE` | Удаление занятий |
| `LESSONS_MARK_ATTENDANCE` | Отметка посещаемости |
| `TASKS_VIEW` | Просмотр задач |
| `TASKS_CREATE` | Создание задач |
| `TASKS_EDIT` | Редактирование задач |
| `TASKS_DELETE` | Удаление задач |
| `SUBSCRIPTIONS_VIEW` | Просмотр абонементов |
| `SUBSCRIPTIONS_CREATE` | Создание абонементов |
| `SUBSCRIPTIONS_EDIT` | Редактирование абонементов |
| `FINANCE_VIEW` | Просмотр финансов |
| `FINANCE_CREATE` | Создание транзакций |
| `FINANCE_EDIT` | Редактирование транзакций |

---

## 5. Регистрация УЦ (публичный)

> Эндпоинт **не требует авторизации** — доступен без JWT токена.

### `POST /api/v1/register` — Зарегистрировать новый учебный центр

**Request Body:**
```json
{
  "firstName": "Иван",
  "lastName": "Петров",
  "centerName": "ABC Учебный Центр",
  "subdomain": "abc-center",
  "email": "ivan@abc.edu",
  "phone": "+7 999 123 4567",
  "password": "password123",
  "confirmPassword": "password123"
}
```

**Валидация полей:**

| Поле | Обязательное | Ограничения |
|------|-------------|-------------|
| `firstName` | да | макс. 100 символов |
| `lastName` | да | макс. 100 символов |
| `centerName` | да | макс. 255 символов |
| `subdomain` | да | 2–100 символов, только `a-z`, `0-9`, `-`, не начинается/не кончается на `-` |
| `email` | да | валидный email |
| `phone` | да | макс. 20 символов |
| `password` | да | минимум 8 символов |
| `confirmPassword` | да | должен совпадать с `password` |

**Response `201 Created`:**
```json
{
  "success": true,
  "message": "Registration successful. You can now log in.",
  "data": {
    "tenantId": "550e8400-e29b-41d4-a716-446655440000",
    "subdomain": "abc-center",
    "adminUsername": "ivan@abc.edu"
  }
}
```

**Что происходит при регистрации:**
1. Создаётся тенант в системе (статус `ACTIVE`, план `BASIC`)
2. Создаётся PostgreSQL схема для тенанта
3. Создаётся пользователь `TENANT_ADMIN` в Keycloak с атрибутом `tenant_id` = UUID тенанта
4. `tenant_id` попадает в JWT через protocol mapper — используй его как `X-Tenant-ID` для последующих запросов
5. Если шаг 3 падает — тенант удаляется (rollback)

**Ошибки:**

| errorCode | Причина |
|-----------|---------|
| `PASSWORD_MISMATCH` | `password` ≠ `confirmPassword` |
| `DUPLICATE_SUBDOMAIN` | субдомен уже занят |
| `USER_CREATION_FAILED` | ошибка создания аккаунта в Keycloak |

**После регистрации** пользователь логинится через Keycloak:
```http
POST https://beta.1edu.kz/auth/realms/ondeedu/protocol/openid-connect/token

grant_type=password&client_id=1edu-web-app&username=ivan@abc.edu&password=password123
```

---

## 6. Tenant Service (8100)

### TenantDto

```typescript
interface TenantDto {
  id: string;                // UUID
  name: string;
  subdomain: string;
  email: string;
  phone: string;
  status: TenantStatus;      // TRIAL | ACTIVE | INACTIVE | SUSPENDED | BANNED
  plan: TenantPlan;          // BASIC | PROFESSIONAL | ENTERPRISE
  schemaName: string;
  timezone: string;
  maxStudents: number;
  maxStaff: number;
  trialEndsAt: string;       // LocalDate ISO
  contactPerson: string;
  notes: string;
  bannedAt: string | null;
  bannedReason: string | null;
  bannedUntil: string | null;
  deletedAt: string | null;
  createdAt: string;         // Instant ISO
  updatedAt: string;
}
```

---

### 5.1 CRUD тенантов (`/api/v1/tenants`)

#### `POST /api/v1/tenants` — Создать тенант
**Доступ:** `SUPER_ADMIN`

**Request Body:**
```json
{
  "name": "Учебный центр ABC",
  "subdomain": "abc",
  "email": "admin@abc.edu",
  "phone": "+998901234567",
  "plan": "BASIC",
  "timezone": "Asia/Tashkent",
  "maxStudents": 500,
  "maxStaff": 20,
  "contactPerson": "Иван Иванов",
  "notes": "Заметки"
}
```

**Response:** `ApiResponse<TenantDto>`

---

#### `GET /api/v1/tenants` — Список тенантов
**Доступ:** `TENANT_ADMIN`

**Query Params:**
- `status` (optional): `TRIAL | ACTIVE | INACTIVE | SUSPENDED | BANNED`
- `page`, `size`

**Response:** `ApiResponse<PageResponse<TenantDto>>`

---

#### `GET /api/v1/tenants/{id}` — Получить тенант
**Доступ:** `TENANT_ADMIN`

**Response:** `ApiResponse<TenantDto>`

---

#### `PUT /api/v1/tenants/{id}` — Обновить тенант
**Доступ:** `TENANT_ADMIN`

**Request Body:**
```json
{
  "name": "Новое название",
  "email": "new@email.com",
  "phone": "+998901234567",
  "status": "ACTIVE",
  "plan": "PROFESSIONAL",
  "timezone": "Asia/Tashkent",
  "maxStudents": 1000,
  "maxStaff": 50,
  "trialEndsAt": "2026-06-01",
  "contactPerson": "Петр Петров",
  "notes": "Обновлённые заметки"
}
```

**Response:** `ApiResponse<TenantDto>`

---

#### `DELETE /api/v1/tenants/{id}` — Удалить тенант
**Доступ:** `TENANT_ADMIN`

**Response:** `ApiResponse<Void>`

---

#### `GET /api/v1/tenants/search` — Поиск тенантов
**Доступ:** `TENANT_ADMIN`

**Query Params:**
- `query` (string): поиск по имени / субдомену
- `page`, `size`

**Response:** `ApiResponse<PageResponse<TenantDto>>`

---

### 5.2 Admin управление (`/api/v1/admin`)

#### `GET /api/v1/admin/tenants` — Список тенантов (с фильтром)
**Доступ:** `SUPER_ADMIN`

**Query Params:**
- `status` (optional)

**Response:** `ApiResponse<List<TenantStatsDto>>`

```typescript
interface TenantStatsDto {
  id: string;
  name: string;
  subdomain: string;
  email: string;
  phone: string;
  status: TenantStatus;
  plan: TenantPlan;
  studentsCount: number;
  staffCount: number;
  activeSubscriptions: number;
  monthlyRevenue: number;
  bannedAt: string | null;
  bannedReason: string | null;
  bannedUntil: string | null;
  deletedAt: string | null;
  createdAt: string;
}
```

---

#### `GET /api/v1/admin/tenants/{id}/stats` — Статистика тенанта
**Доступ:** `SUPER_ADMIN`

**Response:** `ApiResponse<TenantStatsDto>`

---

#### `PATCH /api/v1/admin/tenants/{id}/status` — Изменить статус
**Доступ:** `SUPER_ADMIN`

**Request Body:**
```json
{
  "status": "SUSPENDED",
  "reason": "Нарушение условий использования"
}
```

**Response:** `ApiResponse<TenantStatsDto>`

---

#### `PATCH /api/v1/admin/tenants/{id}/plan` — Изменить план
**Доступ:** `SUPER_ADMIN`

**Request Body:**
```json
{
  "plan": "PROFESSIONAL",
  "maxStudents": 1000,
  "maxStaff": 50
}
```

**Response:** `ApiResponse<TenantStatsDto>`

---

#### `POST /api/v1/admin/tenants/{id}/ban` — Заблокировать тенант
**Доступ:** `SUPER_ADMIN`

**Request Body:**
```json
{
  "reason": "Нарушение правил платформы",
  "bannedUntil": "2026-06-01T00:00:00Z"  // null = постоянная блокировка
}
```

**Response:** `ApiResponse<TenantStatsDto>`

---

#### `POST /api/v1/admin/tenants/{id}/unban` — Разблокировать тенант
**Доступ:** `SUPER_ADMIN`

**Response:** `ApiResponse<TenantStatsDto>`

---

#### `GET /api/v1/admin/tenants/banned` — Список заблокированных
**Доступ:** `SUPER_ADMIN`

**Response:** `ApiResponse<List<TenantStatsDto>>`

---

#### `DELETE /api/v1/admin/tenants/{id}` — Soft delete тенанта
**Доступ:** `SUPER_ADMIN`

**Response:** `ApiResponse<TenantStatsDto>`

---

#### `POST /api/v1/admin/tenants/{id}/restore` — Восстановить тенант
**Доступ:** `SUPER_ADMIN`

**Response:** `ApiResponse<TenantStatsDto>`

---

#### `GET /api/v1/admin/tenants/deleted` — Список удалённых тенантов
**Доступ:** `SUPER_ADMIN`

**Response:** `ApiResponse<List<TenantStatsDto>>`

---

#### `DELETE /api/v1/admin/tenants/{id}/permanent` — Hard delete тенанта
**Доступ:** `SUPER_ADMIN`

> ⚠️ Удаляет запись из БД и дропает PostgreSQL схему тенанта. Необратимо!

**Response:** `ApiResponse<Void>`

---

### 5.3 Super Admin Аналитика (`/api/v1/admin/analytics`)

#### `GET /api/v1/admin/analytics/platform` — Platform KPIs
**Доступ:** `SUPER_ADMIN`

**Response:**
```typescript
interface PlatformKpiResponse {
  totalTenants: number;
  activeTenants: number;
  trialTenants: number;
  suspendedTenants: number;
  inactiveTenants: number;
  activeRate: number;               // активные / всего
  trialConversionRate: number;      // активные / (активные + триал)
  platformMrrEstimate: number;      // MRR (monthly recurring revenue)
  platformArrEstimate: number;      // ARR
  platformRevenueAllTime: number;
  arpu: number;                     // MRR / activeTenants
  totalStudents: number;
  totalStaff: number;
  totalActiveSubs: number;
  avgStudentsPerTenant: number;
  basicCount: number;
  professionalCount: number;
  enterpriseCount: number;
}
```

---

#### `GET /api/v1/admin/analytics/revenue-trend` — Выручка по месяцам
**Доступ:** `SUPER_ADMIN`

**Query Params:**
- `months` (int, 1–24): количество месяцев назад

**Response:** `ApiResponse<List<RevenueTrendDto>>`

```typescript
interface RevenueTrendDto {
  month: string;       // "2026-01"
  revenue: number;
}
```

---

#### `GET /api/v1/admin/analytics/tenant-growth` — Рост тенантов
**Доступ:** `SUPER_ADMIN`

**Query Params:**
- `months` (int, 1–24)

**Response:** `ApiResponse<List<TenantGrowthDto>>`

```typescript
interface TenantGrowthDto {
  month: string;
  newTenants: number;
  churnedTenants: number;
  netGrowth: number;
}
```

---

#### `GET /api/v1/admin/analytics/churn` — Churn Rate
**Доступ:** `SUPER_ADMIN`

**Response:**
```typescript
interface ChurnAnalyticsResponse {
  churnRate30d: number;
  churnRate90d: number;
  churnByPlan: {
    plan: string;
    churnRate: number;
    churnedCount: number;
  }[];
}
```

---

## 7. Auth Service (8101)

### UserDto

```typescript
interface UserDto {
  id: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: string[];
  enabled: boolean;
  photoUrl: string | null;
  language: string | null;
}
```

---

### 6.1 Управление пользователями (`/api/v1/auth/users`)

#### `POST /api/v1/auth/users` — Создать пользователя (сотрудника)
**Доступ:** `TENANT_ADMIN`

**Request Body:**
```json
{
  "username": "teacher.ivan",
  "email": "ivan@abc.edu",
  "firstName": "Иван",
  "lastName": "Иванов",
  "password": "password123",
  "role": "TEACHER",
  "tenantId": "uuid",
  "permissions": ["STUDENTS_VIEW", "LESSONS_CREATE"]
}
```

**Response:** `ApiResponse<UserDto>`

---

#### `GET /api/v1/auth/users` — Список пользователей
**Доступ:** `TENANT_ADMIN`

**Query Params:**
- `search` (string): поиск по имени/email
- `page`, `size`

**Response:** `ApiResponse<List<UserDto>>`

---

#### `GET /api/v1/auth/users/{id}` — Получить пользователя
**Доступ:** `TENANT_ADMIN`, `MANAGER`

**Response:** `ApiResponse<UserDto>`

---

#### `PUT /api/v1/auth/users/{id}` — Обновить пользователя
**Доступ:** `TENANT_ADMIN`

**Request Body:**
```json
{
  "email": "new@email.com",
  "firstName": "Иван",
  "lastName": "Петров",
  "role": "MANAGER",
  "permissions": ["STUDENTS_VIEW", "LEADS_CREATE"]
}
```

**Response:** `ApiResponse<UserDto>`

---

#### `DELETE /api/v1/auth/users/{id}` — Деактивировать пользователя (soft delete)
**Доступ:** `TENANT_ADMIN`

**Response:** `ApiResponse<Void>`

---

#### `POST /api/v1/auth/users/{id}/reset-password` — Сбросить пароль
**Доступ:** `TENANT_ADMIN`

**Request Body:**
```json
{
  "newPassword": "newSecurePassword123"
}
```

**Response:** `ApiResponse<Void>`

---

#### `PUT /api/v1/auth/users/{id}/permissions` — Обновить права пользователя
**Доступ:** `TENANT_ADMIN`

**Request Body:**
```json
["STUDENTS_VIEW", "STUDENTS_CREATE", "LESSONS_VIEW"]
```

**Response:** `ApiResponse<UserDto>`

---

### 6.2 Профиль текущего пользователя (`/api/v1/auth/profile`)

#### `GET /api/v1/auth/profile` — Получить свой профиль
**Доступ:** Любой аутентифицированный

**Response:** `ApiResponse<UserDto>`

---

#### `PUT /api/v1/auth/profile` — Обновить профиль
**Доступ:** Любой аутентифицированный

**Request Body:**
```json
{
  "firstName": "Иван",
  "lastName": "Иванов",
  "photoUrl": "https://cdn.example.com/photo.jpg",
  "language": "ru"
}
```

**Response:** `ApiResponse<UserDto>`

---

#### `POST /api/v1/auth/profile/change-password` — Сменить свой пароль
**Доступ:** Любой аутентифицированный

**Request Body:**
```json
{
  "currentPassword": "oldPassword",
  "newPassword": "newPassword123",
  "confirmPassword": "newPassword123"
}
```

**Response:** `ApiResponse<Void>`

---

## 7. Student Service (8102)

### StudentDto

```typescript
interface StudentDto {
  id: string;
  firstName: string;
  lastName: string;
  middleName: string | null;
  fullName: string;
  email: string | null;
  phone: string | null;
  birthDate: string | null;      // "YYYY-MM-DD"
  status: StudentStatus;          // ACTIVE | INACTIVE | GRADUATED | DROPPED | ON_HOLD
  parentName: string | null;
  parentPhone: string | null;
  address: string | null;
  city: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}
```

---

### 7.1 CRUD студентов (`/api/v1/students`)

#### `POST /api/v1/students` — Создать студента
**Доступ:** `TENANT_ADMIN` | `STUDENTS_CREATE`

**Request Body:**
```json
{
  "firstName": "Алиса",
  "lastName": "Иванова",
  "middleName": "Петровна",
  "email": "alice@example.com",
  "phone": "+998901234567",
  "birthDate": "2005-05-15",
  "parentName": "Иванова Мария",
  "parentPhone": "+998901234568",
  "address": "ул. Пушкина, д. 1",
  "city": "Ташкент",
  "notes": "Занимается английским"
}
```

**Валидация:**
- `firstName`, `lastName`: обязательные, макс. 100 символов
- `email`: формат email (необязательный)
- `phone`: макс. 20 символов

**Response:** `ApiResponse<StudentDto>`

---

#### `GET /api/v1/students` — Список студентов
**Доступ:** `TENANT_ADMIN` | `STUDENTS_VIEW`

**Query Params:**
- `status` (optional): `ACTIVE | INACTIVE | GRADUATED | DROPPED | ON_HOLD`
- `page`, `size`

**Response:** `ApiResponse<PageResponse<StudentDto>>`

---

#### `GET /api/v1/students/{id}` — Получить студента
**Доступ:** `TENANT_ADMIN` | `STUDENTS_VIEW`

**Response:** `ApiResponse<StudentDto>`

---

#### `PUT /api/v1/students/{id}` — Обновить студента
**Доступ:** `TENANT_ADMIN` | `STUDENTS_EDIT`

**Request Body:** (все поля опциональны)
```json
{
  "firstName": "Алиса",
  "lastName": "Петрова",
  "status": "INACTIVE",
  "phone": "+998901111111"
}
```

**Response:** `ApiResponse<StudentDto>`

---

#### `DELETE /api/v1/students/{id}` — Удалить студента
**Доступ:** `TENANT_ADMIN` | `STUDENTS_DELETE`

**Response:** `ApiResponse<Void>`

---

#### `GET /api/v1/students/search` — Поиск студентов
**Доступ:** `TENANT_ADMIN` | `STUDENTS_VIEW`

**Query Params:**
- `query` (string): поиск по ФИО / телефону / email
- `page`, `size`

**Response:** `ApiResponse<PageResponse<StudentDto>>`

---

#### `GET /api/v1/students/group/{groupId}` — Студенты группы
**Доступ:** `TENANT_ADMIN` | `STUDENTS_VIEW`

**Path Params:**
- `groupId` (UUID): ID группы (Schedule)

**Response:** `ApiResponse<PageResponse<StudentDto>>`

---

#### `POST /api/v1/students/{studentId}/groups/{groupId}` — Записать в группу
**Доступ:** `TENANT_ADMIN` | `STUDENTS_EDIT`

**Response:** `ApiResponse<Void>`

---

#### `DELETE /api/v1/students/{studentId}/groups/{groupId}` — Удалить из группы
**Доступ:** `TENANT_ADMIN` | `STUDENTS_EDIT`

**Response:** `ApiResponse<Void>`

---

#### `GET /api/v1/students/stats` — Статистика студентов
**Доступ:** `TENANT_ADMIN` | `STUDENTS_VIEW`

**Response:**
```typescript
interface StudentStatsDto {
  totalStudents: number;
  activeStudents: number;
  newThisMonth: number;
  graduated: number;
  dropped: number;
}
```

---

## 8. Lead Service (8104)

### LeadDto

```typescript
interface LeadDto {
  id: string;
  firstName: string;
  lastName: string;
  fullName: string;
  phone: string | null;
  email: string | null;
  stage: LeadStage;            // NEW | CONTACTED | QUALIFIED | TRIAL | NEGOTIATION | WON | LOST
  source: string | null;
  courseInterest: string | null;
  notes: string | null;
  assignedTo: string | null;   // UUID сотрудника
  createdAt: string;
  updatedAt: string;
}
```

---

### 8.1 CRUD лидов (`/api/v1/leads`)

#### `POST /api/v1/leads` — Создать лид
**Доступ:** `TENANT_ADMIN` | `LEADS_CREATE`

**Request Body:**
```json
{
  "firstName": "Мария",
  "lastName": "Сидорова",
  "phone": "+998907654321",
  "email": "maria@example.com",
  "source": "Instagram",
  "courseInterest": "Английский язык",
  "notes": "Интересуется групповыми занятиями"
}
```

**Response:** `ApiResponse<LeadDto>`

---

#### `GET /api/v1/leads` — Список лидов
**Доступ:** `TENANT_ADMIN` | `LEADS_VIEW`

**Query Params:**
- `stage` (optional): `NEW | CONTACTED | QUALIFIED | TRIAL | NEGOTIATION | WON | LOST`
- `page`, `size`

**Response:** `ApiResponse<PageResponse<LeadDto>>`

---

#### `GET /api/v1/leads/{id}` — Получить лид
**Доступ:** `TENANT_ADMIN` | `LEADS_VIEW`

**Response:** `ApiResponse<LeadDto>`

---

#### `PUT /api/v1/leads/{id}` — Обновить лид
**Доступ:** `TENANT_ADMIN` | `LEADS_EDIT`

**Request Body:**
```json
{
  "firstName": "Мария",
  "lastName": "Петрова",
  "stage": "CONTACTED",
  "assignedTo": "staff-uuid",
  "notes": "Перезвонить завтра"
}
```

**Response:** `ApiResponse<LeadDto>`

---

#### `PATCH /api/v1/leads/{id}/stage` — Изменить этап лида
**Доступ:** `TENANT_ADMIN` | `LEADS_EDIT`

**Query Params:**
- `stage`: `NEW | CONTACTED | QUALIFIED | TRIAL | NEGOTIATION | WON | LOST`

**Response:** `ApiResponse<LeadDto>`

---

#### `DELETE /api/v1/leads/{id}` — Удалить лид
**Доступ:** `TENANT_ADMIN` | `LEADS_DELETE`

**Response:** `ApiResponse<Void>`

---

#### `GET /api/v1/leads/search` — Поиск лидов
**Доступ:** `TENANT_ADMIN` | `LEADS_VIEW`

**Query Params:**
- `query`: поиск по ФИО / телефону
- `page`, `size`

**Response:** `ApiResponse<PageResponse<LeadDto>>`

---

## 9. Course Service (8106)

### CourseDto

```typescript
interface CourseDto {
  id: string;
  type: CourseType;           // GROUP | INDIVIDUAL
  format: CourseFormat;       // OFFLINE | ONLINE
  name: string;
  description: string | null;
  basePrice: number | null;
  enrollmentLimit: number | null;
  color: string | null;       // hex, e.g. "#4CAF50"
  status: CourseStatus;       // ACTIVE | INACTIVE | ARCHIVED
  teacherId: string | null;
  roomId: string | null;
  createdAt: string;
  updatedAt: string;
}
```

---

### 9.1 CRUD курсов (`/api/v1/courses`)

#### `POST /api/v1/courses` — Создать курс
**Доступ:** `TENANT_ADMIN` | `GROUPS_CREATE`

**Request Body:**
```json
{
  "type": "GROUP",
  "format": "OFFLINE",
  "name": "Английский язык A1",
  "description": "Курс для начинающих",
  "basePrice": 500000,
  "enrollmentLimit": 15,
  "color": "#2196F3",
  "teacherId": "uuid",
  "roomId": "uuid"
}
```

**Response:** `ApiResponse<CourseDto>`

---

#### `GET /api/v1/courses` — Список курсов
**Доступ:** `TENANT_ADMIN` | `GROUPS_VIEW`

**Query Params:**
- `status` (optional): `ACTIVE | INACTIVE | ARCHIVED`
- `type` (optional): `GROUP | INDIVIDUAL`
- `page`, `size`

**Response:** `ApiResponse<PageResponse<CourseDto>>`

---

#### `GET /api/v1/courses/{id}` — Получить курс
**Доступ:** `TENANT_ADMIN` | `GROUPS_VIEW`

**Response:** `ApiResponse<CourseDto>`

---

#### `PUT /api/v1/courses/{id}` — Обновить курс
**Доступ:** `TENANT_ADMIN` | `GROUPS_EDIT`

**Request Body:** (все поля опциональны)
```json
{
  "name": "Новое название",
  "status": "INACTIVE",
  "basePrice": 600000
}
```

**Response:** `ApiResponse<CourseDto>`

---

#### `DELETE /api/v1/courses/{id}` — Удалить курс
**Доступ:** `TENANT_ADMIN` | `GROUPS_DELETE`

**Response:** `ApiResponse<Void>`

---

#### `GET /api/v1/courses/search` — Поиск курсов
**Доступ:** `TENANT_ADMIN` | `GROUPS_VIEW`

**Query Params:**
- `query`: поиск по названию
- `page`, `size`

**Response:** `ApiResponse<PageResponse<CourseDto>>`

---

#### `GET /api/v1/courses/teacher/{teacherId}` — Курсы преподавателя
**Доступ:** `TENANT_ADMIN` | `GROUPS_VIEW`

**Response:** `ApiResponse<PageResponse<CourseDto>>`

---

## 10. Schedule Service (8108)

### RoomDto

```typescript
interface RoomDto {
  id: string;
  name: string;
  capacity: number | null;
  description: string | null;
  color: string | null;
  status: RoomStatus;    // ACTIVE | INACTIVE
  createdAt: string;
  updatedAt: string;
}
```

### ScheduleDto (Группа)

```typescript
interface ScheduleDto {
  id: string;
  name: string;
  courseId: string | null;
  teacherId: string | null;
  roomId: string | null;
  daysOfWeek: DayOfWeek[];    // MONDAY | TUESDAY | WEDNESDAY | THURSDAY | FRIDAY | SATURDAY | SUNDAY
  startTime: string;          // "HH:mm:ss"
  endTime: string;            // "HH:mm:ss"
  startDate: string;          // "YYYY-MM-DD"
  endDate: string | null;
  maxStudents: number | null;
  status: ScheduleStatus;     // ACTIVE | PAUSED | COMPLETED
  createdAt: string;
  updatedAt: string;
}
```

---

### 10.1 Аудитории (`/api/v1/rooms`)

#### `POST /api/v1/rooms` — Создать аудиторию
**Доступ:** `TENANT_ADMIN` | `MANAGER`

**Request Body:**
```json
{
  "name": "Аудитория 101",
  "capacity": 20,
  "description": "Большой класс",
  "color": "#FF5722"
}
```

**Response:** `ApiResponse<RoomDto>`

---

#### `GET /api/v1/rooms` — Список аудиторий
**Доступ:** Все роли

**Query Params:**
- `status` (optional): `ACTIVE | INACTIVE`
- `page`, `size`

**Response:** `ApiResponse<PageResponse<RoomDto>>`

---

#### `GET /api/v1/rooms/{id}` — Получить аудиторию
**Доступ:** Все роли

**Response:** `ApiResponse<RoomDto>`

---

#### `PUT /api/v1/rooms/{id}` — Обновить аудиторию
**Доступ:** `TENANT_ADMIN` | `MANAGER`

**Request Body:**
```json
{
  "name": "Аудитория 101 (обновлена)",
  "capacity": 25,
  "status": "INACTIVE"
}
```

**Response:** `ApiResponse<RoomDto>`

---

#### `DELETE /api/v1/rooms/{id}` — Удалить аудиторию
**Доступ:** `TENANT_ADMIN` | `MANAGER`

**Response:** `ApiResponse<Void>`

---

#### `GET /api/v1/rooms/search` — Поиск аудиторий
**Query Params:** `query`, `page`, `size`

**Response:** `ApiResponse<PageResponse<RoomDto>>`

---

### 10.2 Расписание / Группы (`/api/v1/schedules`)

#### `POST /api/v1/schedules` — Создать группу/расписание
**Доступ:** `TENANT_ADMIN` | `GROUPS_CREATE`

**Request Body:**
```json
{
  "name": "Группа English A1 — Утро",
  "courseId": "uuid",
  "teacherId": "uuid",
  "roomId": "uuid",
  "daysOfWeek": ["MONDAY", "WEDNESDAY", "FRIDAY"],
  "startTime": "09:00:00",
  "endTime": "10:30:00",
  "startDate": "2026-02-01",
  "endDate": "2026-06-30",
  "maxStudents": 12
}
```

**Response:** `ApiResponse<ScheduleDto>`

---

#### `GET /api/v1/schedules` — Список групп/расписаний
**Доступ:** `TENANT_ADMIN` | `GROUPS_VIEW`

**Query Params:**
- `status` (optional): `ACTIVE | PAUSED | COMPLETED`
- `courseId` (optional): UUID
- `teacherId` (optional): UUID
- `page`, `size`

**Response:** `ApiResponse<PageResponse<ScheduleDto>>`

---

#### `GET /api/v1/schedules/{id}` — Получить расписание
**Доступ:** `TENANT_ADMIN` | `GROUPS_VIEW`

**Response:** `ApiResponse<ScheduleDto>`

---

#### `PUT /api/v1/schedules/{id}` — Обновить расписание
**Доступ:** `TENANT_ADMIN` | `GROUPS_EDIT`

**Request Body:** (все поля опциональны)
```json
{
  "name": "Группа English A1 — Вечер",
  "startTime": "18:00:00",
  "endTime": "19:30:00",
  "status": "PAUSED"
}
```

**Response:** `ApiResponse<ScheduleDto>`

---

#### `DELETE /api/v1/schedules/{id}` — Удалить расписание
**Доступ:** `TENANT_ADMIN` | `GROUPS_DELETE`

**Response:** `ApiResponse<Void>`

---

#### `GET /api/v1/schedules/room/{roomId}` — Расписание аудитории
**Доступ:** `TENANT_ADMIN` | `GROUPS_VIEW`

**Response:** `ApiResponse<PageResponse<ScheduleDto>>`

---

#### `GET /api/v1/schedules/search` — Поиск расписаний
**Query Params:** `query`, `page`, `size`

**Response:** `ApiResponse<PageResponse<ScheduleDto>>`

---

## 11. Payment Service (8110)

### SubscriptionDto

```typescript
interface SubscriptionDto {
  id: string;
  studentId: string;
  courseId: string | null;
  priceListId: string | null;
  totalLessons: number;
  lessonsLeft: number;
  startDate: string;            // "YYYY-MM-DD"
  endDate: string | null;
  amount: number;
  currency: string;             // "UZS"
  status: SubscriptionStatus;  // ACTIVE | EXPIRED | CANCELLED | FROZEN
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}
```

### PriceListDto

```typescript
interface PriceListDto {
  id: string;
  name: string;
  courseId: string | null;
  price: number;
  lessonsCount: number;
  validityDays: number;
  isActive: boolean;
  description: string | null;
  createdAt: string;
}
```

---

### 11.1 Прайс-листы (`/api/v1/price-lists`)

#### `POST /api/v1/price-lists` — Создать прайс-лист
**Доступ:** `TENANT_ADMIN` | `MANAGER`

**Request Body:**
```json
{
  "name": "Стандарт 24 занятия",
  "courseId": "uuid",
  "price": 800000,
  "lessonsCount": 24,
  "validityDays": 30,
  "isActive": true,
  "description": "Месячный абонемент"
}
```

**Response:** `ApiResponse<PriceListDto>`

---

#### `GET /api/v1/price-lists` — Список прайс-листов
**Доступ:** Все роли

**Query Params:**
- `active` (boolean, optional)
- `page`, `size`

**Response:** `ApiResponse<PageResponse<PriceListDto>>`

---

#### `GET /api/v1/price-lists/{id}` — Получить прайс-лист
**Доступ:** Все роли

**Response:** `ApiResponse<PriceListDto>`

---

#### `PUT /api/v1/price-lists/{id}` — Обновить прайс-лист
**Доступ:** `TENANT_ADMIN` | `MANAGER`

**Response:** `ApiResponse<PriceListDto>`

---

#### `DELETE /api/v1/price-lists/{id}` — Удалить прайс-лист
**Доступ:** `TENANT_ADMIN` | `MANAGER`

**Response:** `ApiResponse<Void>`

---

#### `GET /api/v1/price-lists/course/{courseId}` — Прайс-листы курса
**Доступ:** Все роли

**Response:** `ApiResponse<PageResponse<PriceListDto>>`

---

### 11.2 Абонементы (`/api/v1/subscriptions`)

#### `POST /api/v1/subscriptions` — Создать абонемент
**Доступ:** `TENANT_ADMIN` | `SUBSCRIPTIONS_CREATE`

**Request Body:**
```json
{
  "studentId": "uuid",
  "courseId": "uuid",
  "priceListId": "uuid",
  "totalLessons": 24,
  "startDate": "2026-02-01",
  "endDate": "2026-03-01",
  "amount": 800000,
  "currency": "UZS",
  "notes": "Оплата наличными"
}
```

**Response:** `ApiResponse<SubscriptionDto>`

---

#### `GET /api/v1/subscriptions` — Список абонементов
**Доступ:** `TENANT_ADMIN` | `SUBSCRIPTIONS_VIEW`

**Query Params:**
- `status` (optional): `ACTIVE | EXPIRED | CANCELLED | FROZEN`
- `courseId` (optional): UUID
- `page`, `size`

**Response:** `ApiResponse<PageResponse<SubscriptionDto>>`

---

#### `GET /api/v1/subscriptions/{id}` — Получить абонемент
**Доступ:** `TENANT_ADMIN` | `SUBSCRIPTIONS_VIEW`

**Response:** `ApiResponse<SubscriptionDto>`

---

#### `PUT /api/v1/subscriptions/{id}` — Обновить абонемент
**Доступ:** `TENANT_ADMIN` | `SUBSCRIPTIONS_EDIT`

**Request Body:**
```json
{
  "lessonsLeft": 20,
  "status": "FROZEN",
  "endDate": "2026-04-01",
  "notes": "Заморозка на месяц"
}
```

**Response:** `ApiResponse<SubscriptionDto>`

---

#### `DELETE /api/v1/subscriptions/{id}/cancel` — Отменить абонемент
**Доступ:** `TENANT_ADMIN` | `SUBSCRIPTIONS_EDIT`

**Response:** `ApiResponse<Void>`

---

#### `GET /api/v1/subscriptions/student/{studentId}` — Абонементы студента
**Доступ:** `TENANT_ADMIN` | `SUBSCRIPTIONS_VIEW`

**Query Params:**
- `status` (optional)
- `page`, `size`

**Response:** `ApiResponse<PageResponse<SubscriptionDto>>`

---

### 11.3 Платежи студентов (`/api/v1/payments/student-payments`)

#### `POST /api/v1/payments/student-payments` — Записать платёж
**Доступ:** `TENANT_ADMIN` | `FINANCE_CREATE`

**Request Body:**
```json
{
  "studentId": "uuid",
  "subscriptionId": "uuid",
  "amount": 400000,
  "paidAt": "2026-02-15",
  "paymentMonth": "2026-02",
  "method": "CASH",
  "notes": "Первый взнос"
}
```

**Методы оплаты:** `CASH | CARD | TRANSFER | OTHER`

**Response:** `ApiResponse<StudentPaymentDto>`

---

#### `GET /api/v1/payments/student-payments/student/{studentId}` — История платежей студента
**Доступ:** `TENANT_ADMIN` | `FINANCE_VIEW`

**Response:**
```typescript
interface StudentPaymentHistoryResponse {
  studentId: string;
  totalDebt: number;
  totalPaid: number;
  subscriptions: SubscriptionPaymentSummaryDto[];
}

interface SubscriptionPaymentSummaryDto {
  subscriptionId: string;
  courseId: string;
  priceListId: string | null;
  totalAmount: number;
  monthlyExpected: number;
  totalMonths: number;
  startDate: string;
  endDate: string | null;
  subscriptionStatus: SubscriptionStatus;
  totalPaid: number;
  totalDebt: number;
  months: MonthlyBreakdownDto[];
}

interface MonthlyBreakdownDto {
  month: string;           // "2026-02"
  expected: number;
  paid: number;
  debt: number;
  status: string;          // PAID | PARTIAL | UNPAID
  payments: StudentPaymentDto[];
}
```

---

#### `GET /api/v1/payments/student-payments/overview` — Месячный отчёт
**Доступ:** `TENANT_ADMIN` | `FINANCE_VIEW`

**Query Params:**
- `month` (optional): `YYYY-MM` (по умолчанию текущий месяц)

**Response:**
```typescript
interface MonthlyOverviewResponse {
  month: string;
  totalStudents: number;
  paidCount: number;
  partialCount: number;
  unpaidCount: number;
  totalExpected: number;
  totalCollected: number;
  totalDebt: number;
  students: MonthlyStudentDto[];
}

interface MonthlyStudentDto {
  studentId: string;
  subscriptionId: string;
  expected: number;
  paid: number;
  debt: number;
  status: string;    // PAID | PARTIAL | UNPAID
}
```

---

#### `GET /api/v1/payments/student-payments/debtors` — Должники
**Доступ:** `TENANT_ADMIN` | `FINANCE_VIEW`

**Response:** `ApiResponse<List<StudentDebtDto>>`

```typescript
interface StudentDebtDto {
  studentId: string;
  subscriptionId: string;
  totalDebt: number;
  debtMonths: number;
  monthlyExpected: number;
}
```

---

#### `DELETE /api/v1/payments/student-payments/{id}` — Удалить платёж
**Доступ:** `TENANT_ADMIN`

**Response:** `ApiResponse<Void>`

---

## 12. Finance Service (8112)

### TransactionDto

```typescript
interface TransactionDto {
  id: string;
  type: TransactionType;         // INCOME | EXPENSE | REFUND
  status: TransactionStatus;     // PENDING | COMPLETED | CANCELLED
  amount: number;
  currency: string;
  category: string | null;
  description: string | null;
  transactionDate: string;       // "YYYY-MM-DD"
  studentId: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}
```

---

### 12.1 Транзакции (`/api/v1/finance/transactions`)

#### `POST /api/v1/finance/transactions` — Создать транзакцию
**Доступ:** `TENANT_ADMIN` | `FINANCE_CREATE`

**Request Body:**
```json
{
  "type": "INCOME",
  "amount": 500000,
  "currency": "UZS",
  "category": "Абонементы",
  "description": "Оплата абонемента Алисы",
  "transactionDate": "2026-02-15",
  "studentId": "uuid",
  "notes": "Наличными"
}
```

**Response:** `ApiResponse<TransactionDto>`

---

#### `GET /api/v1/finance/transactions` — Список транзакций
**Доступ:** `TENANT_ADMIN` | `FINANCE_VIEW`

**Query Params:**
- `type` (optional): `INCOME | EXPENSE | REFUND`
- `page`, `size`

**Response:** `ApiResponse<PageResponse<TransactionDto>>`

---

#### `GET /api/v1/finance/transactions/{id}` — Получить транзакцию
**Доступ:** `TENANT_ADMIN` | `FINANCE_VIEW`

**Response:** `ApiResponse<TransactionDto>`

---

#### `PUT /api/v1/finance/transactions/{id}` — Обновить транзакцию
**Доступ:** `TENANT_ADMIN` | `FINANCE_EDIT`

**Response:** `ApiResponse<TransactionDto>`

---

#### `DELETE /api/v1/finance/transactions/{id}` — Удалить транзакцию
**Доступ:** `TENANT_ADMIN` | `FINANCE_VIEW`

**Response:** `ApiResponse<Void>`

---

#### `GET /api/v1/finance/transactions/by-date` — Транзакции за период
**Доступ:** `TENANT_ADMIN` | `FINANCE_VIEW`

**Query Params:**
- `from`: `YYYY-MM-DD`
- `to`: `YYYY-MM-DD`
- `page`, `size`

**Response:** `ApiResponse<PageResponse<TransactionDto>>`

---

#### `GET /api/v1/finance/transactions/student/{studentId}` — Транзакции студента
**Доступ:** `TENANT_ADMIN` | `FINANCE_VIEW`

**Response:** `ApiResponse<PageResponse<TransactionDto>>`

---

## 13. Analytics Service (8114)

Все эндпоинты аналитики доступны для `TENANT_ADMIN` и `MANAGER`.

---

### 13.1 Главный дашборд

#### `GET /api/v1/analytics/dashboard` — Дашборд руководителя

**Query Params:**
- `from`: `YYYY-MM-DD`
- `to`: `YYYY-MM-DD`
- `lessonType` (optional): `GROUP | INDIVIDUAL | TRIAL`

**Response:**
```typescript
interface DashboardResponse {
  // Посещаемость
  attendanceRate: number;          // % за период
  attendancePrevRate: number;      // % за предыдущий период

  // Загрузка групп
  groupLoadRate: number;
  groupLoadPrevRate: number;

  // Пробные занятия
  trialScheduled: number;
  trialAttended: number;
  trialConversionRate: number;
  trialConversionPrevRate: number;

  // Финансы
  averageCheck: number;
  arpu: number;                    // средний доход на студента
  arpuPrev: number;
  subscriptionsSold: number;
  subscriptionsSoldPrev: number;
  subscriptionsDeltaPct: number;
  revenue: number;
  revenueDeltaPct: number;
  expenses: number;
  profit: number;

  // Движение студентов
  studentsAtStart: number;
  studentsJoined: number;
  studentsJoinedDeltaPct: number;
  studentsLeft: number;
  studentsLeftDeltaPct: number;
  studentsAtEnd: number;
  studentsDelta: number;
  studentsDeltaPct: number;
  activeGroupStudents: number;
  activeIndividualStudents: number;

  // Лиды
  leadsTotal: number;
  leadsDeltaPct: number;
  contractsTotal: number;
  leadsToContractsConversion: number;

  // Удержание
  retentionM1Rate: number;
  retentionM1Delta: number;

  // Топ-сотрудник
  topEmployee: {
    staffId: string;
    fullName: string;
    revenue: number;
    studentsCount: number;
  };

  // Помесячная посещаемость
  monthlyAttendance: { month: string; rate: number }[];
  currentMonthAttendance: number;
  currentMonthAttendanceDelta: number;

  // Списки студентов
  joinedStudents: { studentId: string; fullName: string; joinedAt: string }[];
  leftStudents: { studentId: string; fullName: string; leftAt: string }[];
}
```

---

### 13.2 Сводка за сегодня

#### `GET /api/v1/analytics/today` — Сводка за дату

**Query Params:**
- `date` (optional): `YYYY-MM-DD` (по умолчанию сегодня)

**Response:**
```typescript
interface TodayStatsResponse {
  date: string;
  todayRevenue: number;
  todayExpenses: number;
  newSubscriptions: number;
  conductedLessons: number;
  attendedStudents: number;
  newEnrollments: number;

  // Истекающие абонементы
  expiredSubscriptionsTotal: number;
  expiredByDate: ExpiredSubscriptionDto[];      // истекают в течение 7 дней
  expiredByRemaining: ExpiredSubscriptionDto[]; // осталось ≤2 занятия
  overdue: ExpiredSubscriptionDto[];            // просроченные

  // Должники
  totalDebt: number;
  debtors: { studentId: string; fullName: string; balance: number }[];

  // Неоплаченные посещения (без активного абонемента)
  unpaidVisits: {
    studentId: string;
    studentName: string;
    lessonId: string;
    groupName: string;
    lessonDate: string;
  }[];

  // Дни рождения (ближайшие 7 дней)
  upcomingBirthdays: {
    studentId: string;
    fullName: string;
    birthDate: string;
    daysUntil: number;
    turnsAge: number;
  }[];
}

interface ExpiredSubscriptionDto {
  subscriptionId: string;
  studentId: string;
  studentName: string;
  groupName: string;
  lessonsLeft: number;
  amount: number;
  endDate: string;
  category: string;   // EXPIRING_BY_DATE | EXPIRING_BY_REMAINING | OVERDUE
}
```

---

### 13.3 Финансовый отчёт

#### `GET /api/v1/analytics/finance-report`

**Query Params:**
- `from`: `YYYY-MM-DD`
- `to`: `YYYY-MM-DD`

**Response:**
```typescript
interface FinanceReportResponse {
  revenue: number;
  revenueDeltaPct: number;
  expenses: number;
  expensesDeltaPct: number;
  profit: number;
  profitDeltaPct: number;
  monthly: { month: string; revenue: number; expenses: number; profit: number }[];
  revenueByCategory: { category: string; amount: number }[];
  revenueBySource: { source: string; amount: number }[];
  revenueByGroup: { groupId: string; groupName: string; revenue: number }[];
  expensesByCategory: { category: string; amount: number }[];
  reconciliation: {
    subscriptionRevenue: number;
    transactionRevenue: number;
    difference: number;
  };
}
```

---

### 13.4 Отчёт по абонементам

#### `GET /api/v1/analytics/subscriptions`

**Query Params:**
- `from`: `YYYY-MM-DD`
- `to`: `YYYY-MM-DD`
- `onlySuspicious` (boolean): показать только подозрительные

**Response:**
```typescript
interface SubscriptionReportResponse {
  totalAmount: number;
  totalCount: number;
  suspiciousCount: number;
  rows: {
    subscriptionId: string;
    studentId: string;
    studentName: string;
    serviceName: string;
    amount: number;
    status: string;
    suspicious: boolean;
    suspiciousReason: string | null;
    createdDate: string;
    startDate: string;
    totalLessons: number;
    lessonsLeft: number;
    attendanceCount: number;
  }[];
}
```

---

### 13.5 Воронка продаж

#### `GET /api/v1/analytics/funnel`

**Query Params:** `from`, `to` (YYYY-MM-DD)

**Response:**
```typescript
interface SalesFunnelResponse {
  stages: {
    stage: string;    // NEW | CONTACTED | QUALIFIED | TRIAL | NEGOTIATION | WON | LOST
    count: number;
    pct: number;
    budget: number;
    active: number;
    closed: number;
  }[];
  totalLeads: number;
  totalLeadsDeltaPct: number;
  successfulDeals: number;
  successfulDealsDeltaPct: number;
  failedDeals: number;
  failedDealsDeltaPct: number;
  avgDealDurationDays: number;
  avgDealDurationDeltaDays: number;
  openDeals: number;
  openDealsDeltaPct: number;
}
```

---

### 13.6 Конверсия лидов

#### `GET /api/v1/analytics/lead-conversions`

**Query Params:** `from`, `to`

**Response:**
```typescript
interface LeadConversionResponse {
  stageConversions: { from: string; to: string; count: number; rate: number }[];
  bySource: { source: string; total: number; converted: number; rate: number }[];
  byManager: { managerId: string; managerName: string; total: number; converted: number; rate: number }[];
  avgDaysToContract: number;
  medianDaysP50: number;
  medianDaysP75: number;
  medianDaysP90: number;
  trialScheduled: number;
  trialAttended: number;
  trialConverted30d: number;
  trialScheduledPct: number;
  trialAttendedPct: number;
  trialConverted30dPct: number;
  arpu: number;
  arppu: number;
  avgCheck: number;
  rpr: number;    // rate of purchases repeat
}
```

---

### 13.7 Эффективность менеджеров

#### `GET /api/v1/analytics/managers`

**Query Params:** `from`, `to`

**Response:**
```typescript
interface ManagerEfficiencyResponse {
  rows: {
    managerName: string;
    leadsCount: number;
    contractsCount: number;
    conversionPct: number;
    frtP50Days: number;    // Первое время ответа (медиана)
    frtP75Days: number;
    frtP90Days: number;
  }[];
}
```

---

### 13.8 Аналитика преподавателей

#### `GET /api/v1/analytics/teachers`

**Query Params:** `from`, `to`

**Response:**
```typescript
interface TeacherAnalyticsResponse {
  topEmployee: { staffId: string; fullName: string; revenue: number; studentsCount: number };
  rows: {
    staffId: string;
    fullName: string;
    activeStudents: number;
    subscriptionsSold: number;
    studentsInPeriod: number;
    revenue: number;
    revenueDeltaPct: number;
    totalStudents: number;
    avgTenureMonths: number;
    groupLoadRate: number;
    index: number;    // рейтинг
  }[];
}
```

---

### 13.9 Когортный анализ удержания

#### `GET /api/v1/analytics/retention`

**Query Params:**
- `from`: `YYYY-MM-DD`
- `to`: `YYYY-MM-DD`
- `cohortType` (optional): `MONTHLY | WEEKLY`

**Response:**
```typescript
interface RetentionResponse {
  cohorts: {
    cohort: string;       // "2026 янв."
    cohortKey: string;    // "2026-01"
    size: number;         // количество студентов в когорте
    m0: number;           // % удержания через 0 месяцев (100%)
    m1: number;           // % через 1 месяц
    m2: number;
    m3: number;
    m4: number;
    m5: number;
  }[];
}
```

---

### 13.10 Загрузка групп

#### `GET /api/v1/analytics/group-load`

**Доступ:** `TENANT_ADMIN`, `MANAGER`

**Response:**
```typescript
interface GroupLoadResponse {
  rows: {
    groupId: string;
    groupName: string;
    studentsCount: number;
    capacity: number;
    loadPct: number;    // % загрузки
  }[];
}
```

---

### 13.11 Загрузка аудиторий

#### `GET /api/v1/analytics/room-load`

**Query Params:**
- `from`: `YYYY-MM-DD`
- `to`: `YYYY-MM-DD`
- `timelineDate` (optional): `YYYY-MM-DD` (дополнительный таймлайн на конкретный день)

**Response:**
```typescript
interface RoomLoadResponse {
  rows: {
    roomId: string;
    roomName: string;
    lessonsCount: number;
    totalStudents: number;
    totalCapacity: number;
    loadPct: number;
  }[];
  timelineDate: string | null;
  timeline: {
    roomId: string;
    roomName: string;
    occupancyPct: number;
  }[];
}
```

---

### 13.12 Посещаемость группы

#### `GET /api/v1/analytics/group-attendance/{groupId}`

**Доступ:** `TENANT_ADMIN`, `MANAGER`, `TEACHER`

**Query Params:**
- `from`: `YYYY-MM-DD`
- `to`: `YYYY-MM-DD`

**Response:**
```typescript
interface GroupAttendanceResponse {
  groupId: string;
  groupName: string;
  avgAttendanceRate: number;
  monthly: { month: string; rate: number }[];
}
```

---

## 14. Notification Service (8116)

### NotificationDto

```typescript
interface NotificationDto {
  id: string;
  type: string;               // EMAIL | SMS
  recipientEmail: string | null;
  recipientPhone: string | null;
  subject: string | null;
  body: string;
  status: string;             // PENDING | SENT | FAILED
  errorMessage: string | null;
  sentAt: string | null;
  tenantId: string;
  eventType: string;
  createdAt: string;
}
```

---

### 14.1 Уведомления (`/api/v1/notifications`)

#### `GET /api/v1/notifications` — Список уведомлений
**Доступ:** `TENANT_ADMIN`, `MANAGER`

**Query Params:**
- `type` (optional): `EMAIL | SMS`
- `status` (optional): `PENDING | SENT | FAILED`
- `page`, `size`

**Response:** `ApiResponse<PageResponse<NotificationDto>>`

---

#### `GET /api/v1/notifications/{id}` — Получить уведомление
**Доступ:** `TENANT_ADMIN`, `MANAGER`

**Response:** `ApiResponse<NotificationDto>`

---

## 15. File Service (8118)

### FileUploadResponse

```typescript
interface FileUploadResponse {
  fileName: string;
  originalFileName: string;
  contentType: string;
  size: number;              // bytes
  url: string;               // presigned URL или публичный URL
  bucket: string;
  uploadedAt: string;
}
```

---

### 15.1 Файлы (`/api/v1/files`)

#### `POST /api/v1/files/upload` — Загрузить файл
**Доступ:** Любой аутентифицированный

**Request:** `multipart/form-data`
```
file: <binary>
folder: "avatars"    // необязательно: avatars | documents | reports
```

**Response:** `ApiResponse<FileUploadResponse>`

---

#### `GET /api/v1/files/presigned-url` — Получить временную ссылку на файл
**Доступ:** Любой аутентифицированный

**Query Params:**
- `objectName`: имя объекта в MinIO (из `fileName` при загрузке)

**Response:** `ApiResponse<String>` — временная URL (15 минут)

---

#### `DELETE /api/v1/files` — Удалить файл
**Доступ:** `TENANT_ADMIN`, `MANAGER`

**Query Params:**
- `objectName`: имя объекта в MinIO

**Response:** `ApiResponse<Void>`

---

## 16. Report Service (8120)

### 16.1 Генерация отчётов (`/api/v1/reports`)

#### `GET /api/v1/reports/generate` — Сгенерировать отчёт
**Доступ:** `TENANT_ADMIN`, `MANAGER`

**Query Params:**
- `type`: тип отчёта — `FINANCE | ATTENDANCE | SUBSCRIPTIONS | STAFF | STUDENTS`
- `format`: `PDF | XLSX`
- `from` (optional): `YYYY-MM-DD`
- `to` (optional): `YYYY-MM-DD`

**Response:** Бинарный файл с заголовком:
```http
Content-Type: application/pdf  (или application/vnd.openxmlformats-officedocument.spreadsheetml.sheet)
Content-Disposition: attachment; filename="report_2026-02.pdf"
```

> Используйте `blob` режим в axios для скачивания файла.

---

## 17. Staff Service (8122)

### StaffDto

```typescript
interface StaffDto {
  id: string;
  firstName: string;
  lastName: string;
  middleName: string | null;
  fullName: string;
  email: string | null;
  phone: string | null;
  role: StaffRole;            // TEACHER | MANAGER | RECEPTIONIST | ACCOUNTANT | ADMIN
  status: StaffStatus;        // ACTIVE | ON_LEAVE | DISMISSED
  position: string | null;
  salary: number | null;
  hireDate: string | null;    // "YYYY-MM-DD"
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}
```

---

### 17.1 CRUD сотрудников (`/api/v1/staff`)

#### `POST /api/v1/staff` — Создать сотрудника
**Доступ:** `TENANT_ADMIN` | `STAFF_CREATE`

**Request Body:**
```json
{
  "firstName": "Елена",
  "lastName": "Смирнова",
  "middleName": "Александровна",
  "email": "elena@abc.edu",
  "phone": "+998901234567",
  "role": "TEACHER",
  "position": "Преподаватель английского",
  "salary": 3000000,
  "hireDate": "2026-01-15",
  "notes": "Опыт 5 лет"
}
```

**Response:** `ApiResponse<StaffDto>`

---

#### `GET /api/v1/staff` — Список сотрудников
**Доступ:** `TENANT_ADMIN` | `STAFF_VIEW`

**Query Params:**
- `role` (optional): `TEACHER | MANAGER | RECEPTIONIST | ACCOUNTANT | ADMIN`
- `status` (optional): `ACTIVE | ON_LEAVE | DISMISSED`
- `page`, `size`

**Response:** `ApiResponse<PageResponse<StaffDto>>`

---

#### `GET /api/v1/staff/{id}` — Получить сотрудника
**Доступ:** `TENANT_ADMIN` | `STAFF_VIEW`

**Response:** `ApiResponse<StaffDto>`

---

#### `PUT /api/v1/staff/{id}` — Обновить сотрудника
**Доступ:** `TENANT_ADMIN` | `STAFF_EDIT`

**Request Body:** (все поля опциональны)
```json
{
  "status": "ON_LEAVE",
  "salary": 3500000
}
```

**Response:** `ApiResponse<StaffDto>`

---

#### `DELETE /api/v1/staff/{id}` — Удалить сотрудника
**Доступ:** `TENANT_ADMIN` | `STAFF_DELETE`

**Response:** `ApiResponse<Void>`

---

#### `GET /api/v1/staff/search` — Поиск сотрудников
**Query Params:** `query`, `page`, `size`

**Response:** `ApiResponse<PageResponse<StaffDto>>`

---

## 18. Task Service (8124)

### TaskDto

```typescript
interface TaskDto {
  id: string;
  title: string;
  description: string | null;
  status: TaskStatus;        // TODO | IN_PROGRESS | DONE | CANCELLED
  priority: TaskPriority;    // OVERDUE | DUE_TODAY | DUE_THIS_WEEK | DUE_NEXT_WEEK | MORE_THAN_NEXT_WEEK
  assignedTo: string | null; // UUID сотрудника
  dueDate: string | null;    // "YYYY-MM-DD"
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}
```

---

### 18.1 CRUD задач (`/api/v1/tasks`)

#### `POST /api/v1/tasks` — Создать задачу
**Доступ:** `TENANT_ADMIN` | `TASKS_CREATE`

**Request Body:**
```json
{
  "title": "Позвонить студентке Алисе",
  "description": "Уточнить оплату за февраль",
  "priority": "DUE_TODAY",
  "assignedTo": "staff-uuid",
  "dueDate": "2026-02-15",
  "notes": "Контакт: +998901234567"
}
```

**Response:** `ApiResponse<TaskDto>`

---

#### `GET /api/v1/tasks` — Список задач
**Доступ:** `TENANT_ADMIN` | `TASKS_VIEW`

**Query Params:**
- `status` (optional): `TODO | IN_PROGRESS | DONE | CANCELLED`
- `page`, `size`

**Response:** `ApiResponse<PageResponse<TaskDto>>`

---

#### `GET /api/v1/tasks/{id}` — Получить задачу
**Доступ:** `TENANT_ADMIN` | `TASKS_VIEW`

**Response:** `ApiResponse<TaskDto>`

---

#### `PUT /api/v1/tasks/{id}` — Обновить задачу
**Доступ:** `TENANT_ADMIN` | `TASKS_EDIT`

**Response:** `ApiResponse<TaskDto>`

---

#### `DELETE /api/v1/tasks/{id}` — Удалить задачу
**Доступ:** `TENANT_ADMIN` | `TASKS_DELETE`

**Response:** `ApiResponse<Void>`

---

#### `GET /api/v1/tasks/assignee/{assignedTo}` — Задачи сотрудника
**Доступ:** `TENANT_ADMIN` | `TASKS_VIEW`

**Query Params:**
- `status` (optional)
- `page`, `size`

**Response:** `ApiResponse<PageResponse<TaskDto>>`

---

#### `GET /api/v1/tasks/overdue` — Просроченные задачи
**Доступ:** `TENANT_ADMIN` | `TASKS_VIEW`

**Response:** `ApiResponse<PageResponse<TaskDto>>`

---

#### `GET /api/v1/tasks/search` — Поиск задач
**Query Params:** `query`, `page`, `size`

**Response:** `ApiResponse<PageResponse<TaskDto>>`

---

## 19. Lesson Service (8126)

### LessonDto

```typescript
interface LessonDto {
  id: string;
  groupId: string | null;            // UUID расписания (Schedule)
  serviceId: string | null;          // UUID для индивидуальных занятий
  teacherId: string | null;
  substituteTeacherId: string | null;
  roomId: string | null;
  lessonDate: string;               // "YYYY-MM-DD"
  startTime: string;                // "HH:mm:ss"
  endTime: string;                  // "HH:mm:ss"
  lessonType: LessonType;           // GROUP | INDIVIDUAL | TRIAL
  capacity: number | null;
  status: LessonStatus;             // PLANNED | COMPLETED | CANCELLED | TEACHER_ABSENT | TEACHER_SICK
  topic: string | null;
  homework: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}
```

### AttendanceDto

```typescript
interface AttendanceDto {
  id: string;
  lessonId: string;
  studentId: string;
  status: AttendanceStatus;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}
```

---

### 19.1 Занятия (`/api/v1/lessons`)

#### `POST /api/v1/lessons` — Создать занятие
**Доступ:** `TENANT_ADMIN` | `LESSONS_CREATE`

**Request Body:**
```json
{
  "lessonDate": "2026-02-17",
  "startTime": "09:00:00",
  "endTime": "10:30:00",
  "groupId": "schedule-uuid",
  "teacherId": "staff-uuid",
  "roomId": "room-uuid",
  "lessonType": "GROUP",
  "capacity": 15,
  "topic": "Unit 5: Present Perfect",
  "homework": "Упражнения 1-5, стр. 67",
  "notes": ""
}
```

**Response:** `ApiResponse<LessonDto>`

---

#### `GET /api/v1/lessons` — Список занятий
**Доступ:** `TENANT_ADMIN` | `LESSONS_VIEW`

**Query Params:**
- `type` (optional): `GROUP | INDIVIDUAL | TRIAL`
- `status` (optional): `PLANNED | COMPLETED | CANCELLED | TEACHER_ABSENT | TEACHER_SICK`
- `date` (optional): `YYYY-MM-DD` — конкретный день
- `page`, `size`

**Response:** `ApiResponse<PageResponse<LessonDto>>`

---

#### `GET /api/v1/lessons/{id}` — Получить занятие
**Доступ:** `TENANT_ADMIN` | `LESSONS_VIEW`

**Response:** `ApiResponse<LessonDto>`

---

#### `PUT /api/v1/lessons/{id}` — Обновить занятие
**Доступ:** `TENANT_ADMIN` | `LESSONS_EDIT`

**Response:** `ApiResponse<LessonDto>`

---

#### `DELETE /api/v1/lessons/{id}` — Удалить занятие
**Доступ:** `TENANT_ADMIN` | `LESSONS_DELETE`

**Response:** `ApiResponse<Void>`

---

#### `POST /api/v1/lessons/{id}/complete` — Завершить занятие
**Доступ:** `TENANT_ADMIN` | `LESSONS_EDIT`

**Request Body:**
```json
{
  "topic": "Unit 5: Present Perfect",
  "homework": "Упражнения 1-5, стр. 67"
}
```

**Response:** `ApiResponse<LessonDto>` (status → COMPLETED)

---

#### `POST /api/v1/lessons/{id}/cancel` — Отменить занятие
**Доступ:** `TENANT_ADMIN` | `LESSONS_EDIT`

**Response:** `ApiResponse<LessonDto>` (status → CANCELLED)

---

#### `POST /api/v1/lessons/{id}/teacher-absent` — Пометить: учитель отсутствует
**Доступ:** `TENANT_ADMIN` | `LESSONS_EDIT`

**Response:** `ApiResponse<LessonDto>` (status → TEACHER_ABSENT)

---

#### `POST /api/v1/lessons/{id}/teacher-sick` — Пометить: учитель болеет
**Доступ:** `TENANT_ADMIN` | `LESSONS_EDIT`

**Response:** `ApiResponse<LessonDto>` (status → TEACHER_SICK)

---

#### `POST /api/v1/lessons/{id}/reschedule` — Перенести занятие
**Доступ:** `TENANT_ADMIN` | `LESSONS_EDIT`

**Request Body:**
```json
{
  "newDate": "2026-02-20",
  "newStartTime": "10:00:00",
  "newEndTime": "11:30:00"
}
```

**Response:** `ApiResponse<LessonDto>`

---

#### `GET /api/v1/lessons/group/{groupId}` — Занятия группы
**Доступ:** `TENANT_ADMIN` | `LESSONS_VIEW`

**Query Params:**
- `from`: `YYYY-MM-DD`
- `to`: `YYYY-MM-DD`
- `page`, `size`

**Response:** `ApiResponse<PageResponse<LessonDto>>`

---

#### `GET /api/v1/lessons/teacher/{teacherId}` — Занятия преподавателя
**Доступ:** `TENANT_ADMIN` | `LESSONS_VIEW`

**Response:** `ApiResponse<PageResponse<LessonDto>>`

---

### 19.2 Посещаемость (`/api/v1/lessons/{lessonId}/attendance`)

#### `POST /api/v1/lessons/{lessonId}/attendance` — Отметить посещение
**Доступ:** `TENANT_ADMIN` | `LESSONS_MARK_ATTENDANCE`

**Request Body:**
```json
{
  "studentId": "uuid",
  "status": "ATTENDED",
  "notes": ""
}
```

**Статусы посещаемости:**
- `PLANNED` — занесён, урок не прошёл (по умолчанию)
- `ATTENDED` — посетил
- `ABSENT` — пропустил
- `SICK` — болеет
- `VACATION` — в отпуске
- `AUTO_ATTENDED` — автоматически посетил
- `ONE_TIME_VISIT` — разовое посещение

**Response:** `ApiResponse<AttendanceDto>`

---

#### `POST /api/v1/lessons/{lessonId}/attendance/bulk` — Массовая отметка
**Доступ:** `TENANT_ADMIN` | `LESSONS_MARK_ATTENDANCE`

**Request Body:**
```json
{
  "attendances": [
    { "studentId": "uuid-1", "status": "ATTENDED", "notes": "" },
    { "studentId": "uuid-2", "status": "ABSENT", "notes": "Заболел" }
  ]
}
```

**Response:** `ApiResponse<List<AttendanceDto>>`

---

#### `POST /api/v1/lessons/{lessonId}/attendance/mark-all` — Отметить всех как ATTENDED
**Доступ:** `TENANT_ADMIN` | `LESSONS_MARK_ATTENDANCE`

**Request Body:** `["student-uuid-1", "student-uuid-2"]`

**Response:** `ApiResponse<List<AttendanceDto>>`

---

#### `GET /api/v1/lessons/{lessonId}/attendance` — Список посещаемости занятия
**Доступ:** `TENANT_ADMIN` | `LESSONS_VIEW`

**Response:** `ApiResponse<List<AttendanceDto>>`

---

#### `GET /api/v1/attendance/student/{studentId}` — История посещений студента
**Доступ:** `TENANT_ADMIN`, `MANAGER`, `RECEPTIONIST`, `TEACHER`

**Query Params:** `page`, `size`

**Response:** `ApiResponse<PageResponse<AttendanceDto>>`

---

## 20. Settings Service (8128)

### SettingsDto

```typescript
interface SettingsDto {
  id: string | null;
  createdAt: string | null;
  updatedAt: string | null;

  // Профиль компании
  centerName: string | null;
  mainDirection: string | null;
  directorName: string | null;
  corporateEmail: string | null;
  branchCount: number | null;
  logoUrl: string | null;
  city: string | null;
  workPhone: string | null;
  address: string | null;

  // Реквизиты
  directorBasis: string | null;
  bankAccount: string | null;
  bank: string | null;
  bin: string | null;
  bik: string | null;
  requisites: string | null;

  // Общие настройки
  timezone: string;           // default: "Asia/Tashkent"
  currency: string;           // default: "UZS"
  language: string;           // default: "ru"

  // Рабочие часы
  workingHoursStart: string;  // "HH:mm:ss", default: "09:00:00"
  workingHoursEnd: string;    // "HH:mm:ss", default: "21:00:00"
  slotDurationMin: number;    // default: 30
  workingDays: string;        // JSON, default: ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY"]

  // Занятия
  defaultLessonDurationMin: number;  // default: 60
  trialLessonDurationMin: number;    // default: 45
  maxGroupSize: number;              // default: 20

  // Посещаемость
  autoMarkAttendance: boolean;       // default: false
  attendanceWindowDays: number;      // default: 7

  // Уведомления
  smsEnabled: boolean;               // default: false
  emailEnabled: boolean;             // default: true
  smsSenderName: string | null;

  // Финансы
  latePaymentReminderDays: number;           // default: 3
  subscriptionExpiryReminderDays: number;   // default: 3

  // Бренд
  brandColor: string;                // default: "#4CAF50"
}
```

---

### 20.1 Основные настройки (`/api/v1/settings`)

#### `GET /api/v1/settings` — Получить настройки тенанта
**Доступ:** Все роли

**Response:** `ApiResponse<SettingsDto>`

> Если настройки ещё не созданы, возвращает объект с дефолтными значениями (не сохраняет в БД).

---

#### `PUT /api/v1/settings` — Обновить настройки (upsert)
**Доступ:** `TENANT_ADMIN`

**Request Body:** (все поля опциональны, null-значения игнорируются)
```json
{
  "centerName": "Учебный центр ABC",
  "timezone": "Asia/Tashkent",
  "currency": "UZS",
  "workingHoursStart": "09:00:00",
  "workingHoursEnd": "20:00:00",
  "maxGroupSize": 15,
  "brandColor": "#2196F3"
}
```

**Response:** `ApiResponse<SettingsDto>`

---

### 20.2 Настройка ролей (`/api/v1/settings/roles`)

#### `GET /api/v1/settings/roles/permissions` — Все доступные permission-коды
**Доступ:** `TENANT_ADMIN`

**Response:** `ApiResponse<List<String>>`

---

#### `GET /api/v1/settings/roles` — Конфигурации ролей
**Доступ:** `TENANT_ADMIN`, `MANAGER`

**Response:** `ApiResponse<List<RoleConfigDto>>`

```typescript
interface RoleConfigDto {
  id: string;
  name: string;
  description: string | null;
  permissions: string[];
}
```

---

#### `POST /api/v1/settings/roles` — Создать конфигурацию роли
**Доступ:** `TENANT_ADMIN`

**Request Body:**
```json
{
  "name": "Менеджер по продажам",
  "description": "Доступ к лидам и записям",
  "permissions": ["LEADS_VIEW", "LEADS_CREATE", "STUDENTS_VIEW"]
}
```

**Response:** `ApiResponse<RoleConfigDto>`

---

#### `PUT /api/v1/settings/roles/{id}` — Обновить конфигурацию роли
**Доступ:** `TENANT_ADMIN`

**Response:** `ApiResponse<RoleConfigDto>`

---

#### `DELETE /api/v1/settings/roles/{id}` — Удалить конфигурацию роли
**Доступ:** `TENANT_ADMIN`

**Response:** `ApiResponse<Void>`

---

### 20.3 Источники оплаты (`/api/v1/settings/payment-sources`)

#### `GET /api/v1/settings/payment-sources` — Список источников
**Доступ:** `TENANT_ADMIN`, `MANAGER`, `RECEPTIONIST`

**Response:** `ApiResponse<List<PaymentSourceDto>>`

```typescript
interface PaymentSourceDto {
  id: string;
  name: string;
  sortOrder: number;
  active: boolean;
}
```

---

#### `POST /api/v1/settings/payment-sources` — Создать источник
**Доступ:** `TENANT_ADMIN`

**Request Body:**
```json
{
  "name": "Payme",
  "sortOrder": 1,
  "active": true
}
```

**Response:** `ApiResponse<PaymentSourceDto>`

---

#### `PUT /api/v1/settings/payment-sources/{id}` — Обновить источник
**Доступ:** `TENANT_ADMIN`

**Response:** `ApiResponse<PaymentSourceDto>`

---

#### `DELETE /api/v1/settings/payment-sources/{id}` — Удалить источник
**Доступ:** `TENANT_ADMIN`

**Response:** `ApiResponse<Void>`

---

### 20.4 Настройка статусов посещаемости (`/api/v1/settings/attendance-statuses`)

#### `GET /api/v1/settings/attendance-statuses` — Список статусов
**Доступ:** Все роли

**Response:** `ApiResponse<List<AttendanceStatusConfigDto>>`

```typescript
interface AttendanceStatusConfigDto {
  id: string;
  name: string;             // Отображаемое название
  deductLesson: boolean;    // Списывать ли занятие с абонемента
  requirePayment: boolean;  // Требуется ли оплата
  countAsAttended: boolean; // Считать ли посещением
  color: string;            // hex цвет для UI
  sortOrder: number;
  systemStatus: boolean;    // true = системный, нельзя удалить
}
```

---

#### `POST /api/v1/settings/attendance-statuses` — Создать статус
**Доступ:** `TENANT_ADMIN`

**Request Body:**
```json
{
  "name": "Пробное занятие",
  "deductLesson": false,
  "requirePayment": false,
  "countAsAttended": true,
  "color": "#9C27B0",
  "sortOrder": 5
}
```

**Response:** `ApiResponse<AttendanceStatusConfigDto>`

---

#### `PUT /api/v1/settings/attendance-statuses/{id}` — Обновить статус
**Доступ:** `TENANT_ADMIN`

**Response:** `ApiResponse<AttendanceStatusConfigDto>`

---

#### `DELETE /api/v1/settings/attendance-statuses/{id}` — Удалить статус
**Доступ:** `TENANT_ADMIN`

**Response:** `ApiResponse<Void>`

---

## 21. Audit Service (8130)

### 21.1 Аудит-лог (`/api/v1/audit`)

#### `GET /api/v1/audit/system` — Системный лог
**Доступ:** `SUPER_ADMIN`

**Query Params:**
- `action` (optional): строка действия
- `targetId` (optional): UUID цели
- `actorId` (optional): UUID актора
- `from` (optional): ISO datetime
- `to` (optional): ISO datetime
- `page`, `size`

**Response:** `ApiResponse<PageResponse<SystemAuditLog>>`

```typescript
interface SystemAuditLog {
  id: string;           // MongoDB ObjectId
  action: string;       // e.g. "BAN_TENANT", "CHANGE_PLAN"
  targetId: string;
  actorId: string;
  details: Record<string, any>;
  timestamp: string;
}
```

---

#### `GET /api/v1/audit/tenant` — Лог тенанта
**Доступ:** `TENANT_ADMIN`, `SUPER_ADMIN`

> `SUPER_ADMIN` должен передавать `X-Tenant-ID` заголовок.

**Query Params:**
- `category` (optional): категория события
- `action` (optional): строка действия
- `actorId` (optional): UUID
- `from` (optional): ISO datetime
- `to` (optional): ISO datetime
- `page`, `size`

**Response:** `ApiResponse<PageResponse<TenantAuditLog>>`

```typescript
interface TenantAuditLog {
  id: string;
  category: string;     // STUDENTS | PAYMENTS | LESSONS | STAFF | ...
  action: string;       // CREATE | UPDATE | DELETE | ...
  actorId: string;
  targetId: string;
  details: Record<string, any>;
  timestamp: string;
}
```

---

## 22. Справочник Enum-ов

```typescript
// Студент
enum StudentStatus { ACTIVE, INACTIVE, GRADUATED, DROPPED, ON_HOLD }

// Лид
enum LeadStage { NEW, CONTACTED, QUALIFIED, TRIAL, NEGOTIATION, WON, LOST }

// Курс
enum CourseType { GROUP, INDIVIDUAL }
enum CourseFormat { OFFLINE, ONLINE }
enum CourseStatus { ACTIVE, INACTIVE, ARCHIVED }

// Аудитория
enum RoomStatus { ACTIVE, INACTIVE }

// Расписание / Группа
enum ScheduleStatus { ACTIVE, PAUSED, COMPLETED }
enum DayOfWeek { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

// Абонемент
enum SubscriptionStatus { ACTIVE, EXPIRED, CANCELLED, FROZEN }

// Платёж
enum PaymentMethod { CASH, CARD, TRANSFER, OTHER }
enum PaymentStatus { PAID, PARTIAL, UNPAID }

// Финансы
enum TransactionType { INCOME, EXPENSE, REFUND }
enum TransactionStatus { PENDING, COMPLETED, CANCELLED }

// Сотрудник
enum StaffRole { TEACHER, MANAGER, RECEPTIONIST, ACCOUNTANT, ADMIN }
enum StaffStatus { ACTIVE, ON_LEAVE, DISMISSED }

// Задача
enum TaskStatus { TODO, IN_PROGRESS, DONE, CANCELLED }
enum TaskPriority { OVERDUE, DUE_TODAY, DUE_THIS_WEEK, DUE_NEXT_WEEK, MORE_THAN_NEXT_WEEK }

// Занятие
enum LessonType { GROUP, INDIVIDUAL, TRIAL }
enum LessonStatus { PLANNED, COMPLETED, CANCELLED, TEACHER_ABSENT, TEACHER_SICK }

// Посещаемость
enum AttendanceStatus { PLANNED, ATTENDED, ABSENT, SICK, VACATION, AUTO_ATTENDED, ONE_TIME_VISIT }

// Тенант
enum TenantStatus { TRIAL, ACTIVE, INACTIVE, SUSPENDED, BANNED }
enum TenantPlan { BASIC, PROFESSIONAL, ENTERPRISE }
```

---

## Примеры использования (TypeScript/Axios)

### Настройка axios

**.env (локальная разработка → сервер):**
```env
VITE_API_BASE_URL=https://beta.1edu.kz
VITE_KEYCLOAK_URL=https://beta.1edu.kz/auth
VITE_KEYCLOAK_REALM=ondeedu
VITE_KEYCLOAK_CLIENT_ID=crm-frontend
```

**.env.local (если хочешь переключиться на локальный бекенд):**
```env
VITE_API_BASE_URL=http://localhost:8090
VITE_KEYCLOAK_URL=http://localhost:8080
VITE_KEYCLOAK_REALM=ondeedu
VITE_KEYCLOAK_CLIENT_ID=crm-frontend
```

**api.ts:**
```typescript
import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: { 'Content-Type': 'application/json' }
});

// Interceptor для авторизации
api.interceptors.request.use(config => {
  const token = localStorage.getItem('access_token');
  const tenantId = localStorage.getItem('tenant_id');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  if (tenantId) config.headers['X-Tenant-ID'] = tenantId;
  return config;
});

export default api;
```

**keycloak.ts (если используешь keycloak-js):**
```typescript
import Keycloak from 'keycloak-js';

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL,
  realm: import.meta.env.VITE_KEYCLOAK_REALM,
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
});

export default keycloak;
```

### Получение списка студентов

```typescript
const response = await api.get('/api/v1/students', {
  params: { status: 'ACTIVE', page: 0, size: 20 }
});
const { content, totalElements, totalPages } = response.data.data;
```

### Создание студента

```typescript
const response = await api.post('/api/v1/students', {
  firstName: 'Алиса',
  lastName: 'Иванова',
  phone: '+998901234567',
  birthDate: '2005-05-15'
});
const student = response.data.data;
```

### Загрузка файла

```typescript
const formData = new FormData();
formData.append('file', fileInput.files[0]);
formData.append('folder', 'avatars');

const response = await api.post('/api/v1/files/upload', formData, {
  headers: { 'Content-Type': 'multipart/form-data' }
});
const { url } = response.data.data;
```

### Скачивание отчёта

```typescript
const response = await api.get('/api/v1/reports/generate', {
  params: { type: 'FINANCE', format: 'PDF', from: '2026-02-01', to: '2026-02-28' },
  responseType: 'blob'
});

const url = URL.createObjectURL(response.data);
const link = document.createElement('a');
link.href = url;
link.download = 'report.pdf';
link.click();
```

### Отметка посещаемости (bulk)

```typescript
await api.post(`/api/v1/lessons/${lessonId}/attendance/bulk`, {
  attendances: students.map(s => ({
    studentId: s.id,
    status: 'ATTENDED',
    notes: ''
  }))
});
```
