# ApiPay.kz API Documentation

## Complete Integration Guide for Kaspi Pay (Phone Payments)

## Base Configuration

- **Base URL:** `https://bpapi.bazarbay.site/api/v1`
- **Authentication:** Header `X-API-Key: your_api_key`
- **Content-Type:** `application/json`
- **Rate Limits:** 60 req/min per API key

---

## 1edu CRM Integration Notes

Эти пункты описывают поведение именно нашей CRM-интеграции с ApiPay.

- **CRM webhook endpoint:** `https://api.1edu.kz/internal/apipay/webhook`
- **Public ingress:** путь `/internal/apipay/**` проброшен через nginx и api-gateway в `payment-service`
- **Webhook signature header:** `X-Webhook-Signature`
- **Signature format:** `sha256=<hex>` (HMAC-SHA256 по raw body и tenant webhook secret)
- **Tenant resolution в webhook:** по `external_order_id`/`invoice_id` формата
  `<tenantUuidWithoutDashes>_<random>`
- **Invoice side-effect:** при webhook-статусе `PAID` CRM автоматически создаёт запись в `student_payments`
  и связывает её с `apipay_invoices.student_payment_id`

### Phone normalization in CRM

ApiPay ожидает номер в формате `8XXXXXXXXXX`.
CRM перед отправкой инвойса автоматически нормализует номер:

- `+7XXXXXXXXXX` -> `8XXXXXXXXXX`
- `7XXXXXXXXXX` -> `8XXXXXXXXXX`
- `XXXXXXXXXX` -> `8XXXXXXXXXX`

Если номер не приводится к корректному формату, инвойс не отправляется в ApiPay и сохраняется
как failed с ошибкой `RECIPIENT_INVALID`.

---

## Pricing

| Plan | Transaction Limit (per day) | Price/month |
|------|----------------------------|-------------|
| Старт (Start) | до 30/день | 10,000 KZT |
| Бизнес (Business) | от 30/день | 25,000 KZT |
| Про (Pro) | от 100/день | 60,000 KZT |

- Комиссия 0% с платежей — никаких скрытых сборов
- Все тарифы включают ВСЕ функции: счета, подписки, каталог, возвраты, вебхуки
- При превышении лимита — нет блокировки, менеджер свяжется проактивно

---

## Prerequisites

1. Get API key in [ApiPay.kz](https://apipay.kz) dashboard
2. [Connect your Kaspi Business as "Cashier"](https://apipay.kz/connect-cashier) — contact support via WhatsApp (+7 708 516 74 89)
3. Wait for organization verification (usually 5-30 minutes)

---

## Endpoints Overview (22)

| # | Method | Path | Description |
|---|--------|------|-------------|
| | **Invoices** | | |
| 1 | POST | /invoices | Создать счёт |
| 2 | GET | /invoices | Список счетов |
| 3 | GET | /invoices/{id} | Просмотр счёта |
| 4 | POST | /invoices/{id}/cancel | Отменить счёт |
| 5 | POST | /invoices/status/check | Проверить статусы счетов |
| | **Refunds** | | |
| 6 | POST | /invoices/{id}/refund | Возврат по счёту |
| 7 | GET | /invoices/{id}/refunds | Список возвратов по счёту |
| 8 | GET | /refunds | Список возвратов |
| | **Catalog** | | |
| 9 | GET | /catalog/units | Список единиц измерения |
| 10 | GET | /catalog | Список товаров каталога |
| 11 | POST | /catalog/upload-image | Загрузить изображение для каталога |
| 12 | POST | /catalog | Создать товар каталога |
| 13 | PATCH | /catalog/{id} | Обновить товар каталога |
| 14 | DELETE | /catalog/{id} | Удалить товар каталога |
| | **Subscriptions** | | |
| 15 | POST | /subscriptions | Создать подписку |
| 16 | GET | /subscriptions | Список подписок |
| 17 | GET | /subscriptions/{id} | Просмотр подписки |
| 18 | PUT | /subscriptions/{id} | Обновить подписку |
| 19 | POST | /subscriptions/{id}/pause | Приостановить подписку |
| 20 | POST | /subscriptions/{id}/resume | Возобновить подписку |
| 21 | POST | /subscriptions/{id}/cancel | Отменить подписку |
| 22 | GET | /subscriptions/{id}/invoices | История платежей подписки |

---

## Health Check

## Invoices

### GET /invoices

Возвращает пагинированный список счетов с возможностью фильтрации.

**Query parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| search | string | — | Поиск по описанию, телефону, external_order_id. |
| status | array | — | Фильтр по статусу. |
| date_from | string | — | Дата начала (Y-m-d). |
| date_to | string | — | Дата окончания (Y-m-d). |
| sort_by | string | — | Сортировка: id, amount, client_name, status, created_at, paid_at. |
| sort_order | string | — | Порядок: asc, desc. |
| per_page | integer | — | Кол-во на страницу (1-100). |

**Response 200:**
```json
{
  "current_page": 1,
  "data": [{"id":1,"amount":"5000.00","description":"Оплата заказа","external_order_id":"order-123","status":"paid","kaspi_invoice_id":"ABC123","phone":"77001234567","client_name":"Иван Иванов","client_comment":null,"is_sandbox":false,"is_recurring":false,"subtotal":null,"discount_sum":null,"discount_percentage":null,"total_refunded":"0.00","is_fully_refunded":false,"error_message":null,"paid_at":"2026-02-26T10:30:00+06:00","created_at":"2026-02-26T10:25:00+06:00","items":[]}],
  "total": 1
}
```

**Errors:**
- `422` — Ошибка валидации

---

### POST /invoices

Создаёт новый счёт для оплаты через Kaspi Pay.
Для организаций с каталогом используйте `cart_items` вместо `amount`.

**Request:**
```json
{
  "phone_number": "87001234567",
  "description": "Оплата заказа №123",
  "external_order_id": "order-123",
  "amount": 5000,
  "cart_items": [[]],
  "discount_percentage": 10
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| phone_number | string | Yes | Номер телефона. Формат: 8XXXXXXXXXX. |
| description | string | No | Описание счёта. |
| external_order_id | string | No | Внешний ID заказа. |
| amount | number | No | Сумма (обязательна без cart_items). |
| cart_items | array | No | Товары корзины (для организаций с каталогом). |
| discount_percentage | number | No | Скидка на весь чек, 1-99% (только с cart_items). |

**Response 201:**
```json
{
  "id": 1,
  "amount": "5000.00",
  "status": "processing",
  "paid_at": null,
  "phone": "77001234567",
  "created_at": "2026-02-26T10:25:00+06:00"
}
```

**Errors:**
- `400` — 
- `422` — Ошибка валидации
- `503` — Сессия невалидна

---

### GET /invoices/{id}

Возвращает детальную информацию о счёте, включая товары.

**Response 200:**
```json
{
  "id": 1,
  "amount": "5000.00",
  "description": "Оплата заказа",
  "external_order_id": "order-123",
  "status": "paid",
  "kaspi_invoice_id": "INV-123",
  "phone": "77001234567",
  "client_name": "Иван Иванов",
  "client_comment": null,
  "is_sandbox": false,
  "is_recurring": false,
  "subtotal": null,
  "discount_sum": null,
  "discount_percentage": null,
  "total_refunded": "0.00",
  "is_fully_refunded": false,
  "error_message": null,
  "paid_at": "2026-02-26T10:30:00+06:00",
  "created_at": "2026-02-26T10:25:00+06:00",
  "items": []
}
```

**Errors:**
- `404` — 

---

### POST /invoices/{id}/cancel

Отменяет счёт в статусе pending или processing. Для production счетов отмена выполняется асинхронно (статус 202).

**Response 200:**
```json
{
  "message": "Invoice cancelled successfully",
  "invoice": {
  "id": 1,
  "amount": "5000.00",
  "status": "cancelled",
  "phone": "77001234567",
  "created_at": "2026-02-26T10:25:00+06:00"
}
}
```

**Errors:**
- `400` — 
- `404` — 

---

### POST /invoices/{id}/refund

Создаёт возврат по оплаченному счёту. Поддерживает полный и частичный возврат,
а также поэлементный возврат через `return_items`.

**Request:**
```json
{
  "amount": 2500,
  "reason": "Брак товара",
  "return_items": [[]]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| amount | number | No | Сумма возврата (без return_items; по умолчанию — вся доступная сумма). |
| reason | string | No | Причина возврата. |
| return_items | array | No | Товары для возврата (вместо amount). |

**Response 201:**
```json
{
  "message": "Refund created and queued for processing",
  "refund": {
  "id": 1,
  "invoice_id": 1,
  "user_id": 1,
  "api_key_id": 1,
  "amount": "2500.00",
  "kaspi_refund_id": null,
  "kaspi_status": null,
  "status": "pending",
  "reason": "Брак товара",
  "initiated_by": "api_key",
  "error_message": null,
  "created_at": "2026-02-26T11:00:00+06:00"
},
  "invoice": {
  "id": 1,
  "amount": "5000.00",
  "total_refunded": "0.00",
  "available_for_refund": 5000,
  "pending_refund_amount": 2500
}
}
```

**Errors:**
- `400` — Не подлежит возврату
- `404` — 

---

### GET /invoices/{id}/refunds

Возвращает все возвраты по конкретному счёту.

**Response 200:**
```json
{
  "invoice": {
  "id": 1,
  "amount": "5000.00",
  "total_refunded": "2500.00",
  "available_for_refund": 2500,
  "is_fully_refunded": false
},
  "refunds": [{"id":1,"invoice_id":1,"user_id":1,"api_key_id":1,"amount":"2500.00","kaspi_refund_id":null,"kaspi_status":null,"status":"completed","reason":"Брак товара","initiated_by":"api_key","error_message":null,"created_at":"2026-02-26T11:00:00+06:00","items":[]}],
  "total": 1
}
```

**Errors:**
- `404` — 

---

### POST /invoices/status/check

Диспатчит задачи проверки статусов для указанных счетов через Kaspi API.

**Request:**
```json
{
  "invoice_ids": [1,2,3]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| invoice_ids | array | Yes | Массив ID счетов. |

**Response 200:**
```json
{
  "invoices": [{"id":1,"status":"paid","kaspi_invoice_id":"ABC123","amount":"5000.00","error_message":null,"updated_at":"2026-02-26T10:30:00+06:00"}]
}
```

**Errors:**
- `422` — Ошибка валидации

---

### Invoice Statuses

| Status | Description | Can Cancel | Can Refund |
|--------|-------------|------------|------------|
| `pending` | Awaiting payment | Yes | No |
| `cancelling` | Being cancelled (async) | No | No |
| `paid` | Paid by customer | No | Yes |
| `cancelled` | Manually cancelled | No | No |
| `expired` | Payment deadline passed | No | No |
| `partially_refunded` | Partially refunded | No | Yes (remaining) |
| `refunded` | Fully refunded | No | No |

---

## Refunds

### GET /refunds

Возвращает список возвратов организации с фильтрацией и пагинацией.

**Request:**
```json
{
  "status": null,
  "invoice_id": 17,
  "date_from": "2026-04-07",
  "date_to": "2107-05-07",
  "per_page": 13
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| status | array | No |  |
| invoice_id | integer | No | The <code>id</code> of an existing record in the invoices table. |
| date_from | string | No | Must be a valid date. Must be a valid date in the format <code>Y-m-d</code>. |
| date_to | string | No | Must be a valid date. Must be a valid date in the format <code>Y-m-d</code>. Must be a date after or equal to <code>date_from</code>. |
| per_page | integer | No | Must be at least 1. Must not be greater than 100. |

**Query parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| status[] | array | — | Фильтр по статусам. Допустимые значения: `pending`, `processing`, `completed`, `failed`. |
| invoice_id | integer | — | Фильтр по ID счёта. |
| date_from | string | — | Начало периода в формате `Y-m-d`. |
| date_to | string | — | Конец периода в формате `Y-m-d`. |
| per_page | integer | — | Количество записей на страницу (1-100). |

**Response 200:**
```json
{
  "current_page": 1,
  "data": [{"id":1,"invoice_id":1,"user_id":1,"api_key_id":1,"amount":"2500.00","kaspi_refund_id":null,"kaspi_status":null,"status":"completed","reason":"Брак","initiated_by":"api_key","error_message":null,"created_at":"2026-02-26T11:00:00+06:00","invoice":{"id":1,"external_order_id":"ORDER-001","amount":"5000.00","status":"paid","kaspi_invoice_id":"ABC123"},"items":[]}],
  "total": 1
}
```

**Errors:**
- `422` — Ошибка валидации

---

### Refund Statuses

| Status | Description |
|--------|-------------|
| `pending` | Refund created, queued for processing |
| `processing` | Being processed by Kaspi |
| `completed` | Successfully refunded |
| `failed` | Refund failed |

---

## Catalog

### GET /catalog/units

Возвращает список доступных единиц измерения для товаров каталога (шт, кг, л и т.д.).

**Response 200:**
```json
{
  "data": [{"id":1,"name":"шт","name_kaz":"дана"},{"id":2,"name":"кг","name_kaz":"кг"}]
}
```

---

### GET /catalog

Возвращает пагинированный список товаров каталога организации. Поддерживает поиск и фильтрацию.

**Query parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| search | string | — | Поиск по названию товара. |
| barcode | string | — | Фильтр по штрихкоду. |
| first_char | string | — | Фильтр по первой букве названия. |
| per_page | integer | — | Кол-во на страницу (1-200, по умолчанию 50). |

**Response 200:**
```json
{
  "data": [{"id":1,"kaspi_item_id":null,"name":"Кофе","unit_id":1,"selling_price":"1500.00","date_added":null,"image_url":null,"first_char":"К","nds_percentage":null,"barcode":"4607001234567","ntin":null,"status":"active","error_message":null,"created_at":"2026-02-26T10:00:00+06:00","synced_at":"2026-02-26T10:00:00+06:00"}],
  "links": {

},
  "meta": {

}
}
```

---

### POST /catalog

Создаёт один или несколько товаров в каталоге организации. Товары сохраняются локально со статусом pending и отправляются в Kaspi API через очередь.

**Request:**
```json
{
  "items": [[]]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| items | array | Yes | Массив товаров (макс. 50). |

**Response 202:**
```json
{
  "data": [{"id":1,"kaspi_item_id":null,"name":"Кофе Американо","unit_id":1,"selling_price":"1500.00","date_added":null,"image_url":null,"first_char":"К","nds_percentage":null,"barcode":"4607001234567","ntin":null,"status":"pending","error_message":null,"created_at":"2026-02-26T10:00:00+06:00","synced_at":"2026-02-26T10:00:00+06:00"}]
}
```

**Errors:**
- `422` — Ошибка валидации

---

### POST /catalog/upload-image

Загружает и оптимизирует изображение товара. Использует MD5 дедупликацию — повторная загрузка того же изображения вернёт существующий image_id.

**Request:** `multipart/form-data`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| image | file | Yes | Изображение товара (jpeg/png/webp, макс. 2 МБ). |

**Response 200:**
```json
{
  "image_id": "abc123"
}
```

**Errors:**
- `422` — Ошибка валидации

---

### PATCH /catalog/{id}

Обновляет данные товара каталога. Локальные поля обновляются сразу, изменения в Kaspi API отправляются через очередь.

**Request:**
```json
{
  "name": "Кофе Латте",
  "selling_price": 1800,
  "unit_id": 1,
  "image_id": "abc123",
  "is_image_deleted": false,
  "barcode": "4607001234567"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| name | string | No | Название товара. |
| selling_price | number | No | Цена продажи. |
| unit_id | integer | No | ID единицы измерения. |
| image_id | string | No | UUID нового изображения. |
| is_image_deleted | boolean | No | Удалить текущее изображение. |
| barcode | string | No | Штрихкод товара. |

**Response 202:**
```json
{
  "message": "Catalog item update queued",
  "catalog_item_id": 1
}
```

**Errors:**
- `404` — 
- `422` — Ошибка валидации

---

### DELETE /catalog/{id}

Помечает товар как удаляемый и отправляет запрос на удаление в Kaspi API через очередь.

**Response 202:**
```json
{
  "message": "Catalog item deletion queued",
  "catalog_item_id": 1
}
```

**Errors:**
- `404` — 

---

## Subscriptions

### GET /subscriptions

Возвращает пагинированный список подписок с возможностью фильтрации.

**Query parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| status | string | — | Фильтр по статусу (active, paused, cancelled, expired). |
| external_subscriber_id | string | — | Фильтр по внешнему ID подписчика. |
| phone_number | string | — | Фильтр по номеру телефона (8XXXXXXXXXX). |
| per_page | integer | — | Кол-во на страницу (по умолчанию 20). |

**Response 200:**
```json
{
  "current_page": 1,
  "data": [{"id":1,"subscriber_name":"Иван Петров","phone_number":"87001234567","external_subscriber_id":"client-42","amount":"5000.00","cart_items":null,"description":"Ежемесячная подписка","billing_period":"monthly","billing_period_label":"Ежемесячно","billing_day":15,"billing_day_label":"15 числа","status":"active","status_label":"Активна","status_color":"green","started_at":"2026-01-15T00:00:00+00:00","next_billing_at":"2026-03-15T00:00:00+00:00","next_billing_in_days":17,"next_billing_label":"через 17 дней","paused_at":null,"cancelled_at":null,"failed_attempts":0,"max_retry_attempts":3,"retry_interval_hours":24,"grace_period_days":3,"in_grace_period":false,"is_sandbox":false,"metadata":null,"created_at":"2026-01-10T12:00:00+00:00","updated_at":"2026-02-15T10:30:00+00:00"}],
  "total": 1
}
```

---

### POST /subscriptions

Создаёт новую рекуррентную подписку. Первый счёт выставляется автоматически.

**Request:**
```json
{
  "phone_number": "87001234567",
  "billing_period": "monthly",
  "billing_day": 15,
  "description": "Ежемесячная подписка",
  "subscriber_name": "Иван Петров",
  "external_subscriber_id": "client-42",
  "started_at": "2026-03-01",
  "max_retry_attempts": 3,
  "retry_interval_hours": 24,
  "grace_period_days": 3,
  "metadata": {"source":"website"},
  "bill_immediately": false,
  "amount": 5000,
  "cart_items": [[]]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| phone_number | string | Yes | Номер телефона подписчика в формате 8XXXXXXXXXX. |
| billing_period | string | Yes | Период биллинга: daily, weekly, biweekly, monthly, quarterly, yearly. |
| billing_day | integer | No | День биллинга (1-28). |
| description | string | No | Описание подписки. |
| subscriber_name | string | No | Имя подписчика. |
| external_subscriber_id | string | No | Внешний ID подписчика в вашей системе. |
| started_at | string | No | Дата начала (Y-m-d). По умолчанию — сегодня. |
| max_retry_attempts | integer | No | Макс. кол-во повторных попыток при неудаче (1-10). |
| retry_interval_hours | integer | No | Интервал между повторами в часах (1-168). |
| grace_period_days | integer | No | Льготный период в днях (1-30). |
| metadata | object | No | Произвольные метаданные. |
| bill_immediately | boolean | No | Выставить первый счёт сразу при создании. |
| amount | number | No | Сумма списания в тенге (100-1000000). Обязательна для организаций без каталога. |
| cart_items | array | No | Корзина товаров (только для каталожных организаций). |

**Response 201:**
```json
{
  "message": "Subscription created",
  "subscription": {
  "id": 1,
  "subscriber_name": "Иван Петров",
  "phone_number": "87001234567",
  "external_subscriber_id": "client-42",
  "amount": "5000.00",
  "cart_items": null,
  "description": "Ежемесячная подписка",
  "billing_period": "monthly",
  "billing_period_label": "Ежемесячно",
  "billing_day": 15,
  "billing_day_label": "15 числа",
  "status": "active",
  "status_label": "Активна",
  "status_color": "green",
  "started_at": "2026-03-01T00:00:00+00:00",
  "next_billing_at": "2026-03-01T00:00:00+00:00",
  "next_billing_in_days": 3,
  "next_billing_label": "через 3 дня",
  "paused_at": null,
  "cancelled_at": null,
  "failed_attempts": 0,
  "max_retry_attempts": 3,
  "retry_interval_hours": 24,
  "grace_period_days": 3,
  "in_grace_period": false,
  "is_sandbox": false,
  "metadata": {
  "source": "website"
},
  "created_at": "2026-02-26T12:00:00+00:00",
  "updated_at": "2026-02-26T12:00:00+00:00"
}
}
```

**Errors:**
- `400` — Sandbox без организации
- `403` — Организация не верифицирована
- `422` — Ошибка валидации

---

### GET /subscriptions/{id}

Возвращает детальную информацию о подписке, включая статистику и последний платёж.

**Response 200:**
```json
{
  "subscription": {
  "id": 1,
  "subscriber_name": "Иван Петров",
  "phone_number": "87001234567",
  "external_subscriber_id": "client-42",
  "amount": "5000.00",
  "cart_items": null,
  "description": "Ежемесячная подписка",
  "billing_period": "monthly",
  "billing_period_label": "Ежемесячно",
  "billing_day": 15,
  "billing_day_label": "15 числа",
  "status": "active",
  "status_label": "Активна",
  "status_color": "green",
  "started_at": "2026-01-15T00:00:00+00:00",
  "next_billing_at": "2026-03-15T00:00:00+00:00",
  "next_billing_in_days": 17,
  "next_billing_label": "через 17 дней",
  "paused_at": null,
  "cancelled_at": null,
  "failed_attempts": 0,
  "max_retry_attempts": 3,
  "retry_interval_hours": 24,
  "grace_period_days": 3,
  "in_grace_period": false,
  "is_sandbox": false,
  "metadata": null,
  "created_at": "2026-01-10T12:00:00+00:00",
  "updated_at": "2026-02-15T10:30:00+00:00",
  "last_payment": {
  "amount": "5000.00",
  "paid_at": "2026-02-15T10:30:00+00:00",
  "status": "paid"
},
  "stats": {
  "total_payments": 3,
  "successful_payments": 3,
  "failed_payments": 0,
  "total_amount": "15000.00"
}
}
}
```

**Errors:**
- `404` — Подписка не найдена

---

### PUT /subscriptions/{id}

Обновляет параметры существующей подписки. Все поля опциональны.

**Request:**
```json
{
  "amount": 7500,
  "billing_day": 20,
  "description": "Обновлённая подписка",
  "subscriber_name": "Иван Петров",
  "external_subscriber_id": "client-42",
  "max_retry_attempts": 5,
  "retry_interval_hours": 48,
  "grace_period_days": 5,
  "metadata": {"plan":"premium"},
  "cart_items": [[]]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| amount | number | No | Сумма списания в тенге (100-1000000). |
| billing_day | integer | No | День биллинга (1-28). |
| description | string | No | Описание подписки. |
| subscriber_name | string | No | Имя подписчика. |
| external_subscriber_id | string | No | Внешний ID подписчика. |
| max_retry_attempts | integer | No | Макс. кол-во повторных попыток (1-10). |
| retry_interval_hours | integer | No | Интервал между повторами в часах (1-168). |
| grace_period_days | integer | No | Льготный период в днях (1-30). |
| metadata | object | No | Произвольные метаданные. |
| cart_items | array | No | Корзина товаров (только для каталожных организаций). |

**Response 200:**
```json
{
  "message": "Subscription updated",
  "subscription": {
  "id": 1,
  "subscriber_name": "Иван Петров",
  "phone_number": "87001234567",
  "external_subscriber_id": "client-42",
  "amount": "7500.00",
  "cart_items": null,
  "description": "Обновлённая подписка",
  "billing_period": "monthly",
  "billing_period_label": "Ежемесячно",
  "billing_day": 20,
  "billing_day_label": "20 числа",
  "status": "active",
  "status_label": "Активна",
  "status_color": "green",
  "started_at": "2026-01-15T00:00:00+00:00",
  "next_billing_at": "2026-03-20T00:00:00+00:00",
  "next_billing_in_days": 22,
  "next_billing_label": "через 22 дня",
  "paused_at": null,
  "cancelled_at": null,
  "failed_attempts": 0,
  "max_retry_attempts": 5,
  "retry_interval_hours": 48,
  "grace_period_days": 5,
  "in_grace_period": false,
  "is_sandbox": false,
  "metadata": {
  "plan": "premium"
},
  "created_at": "2026-01-10T12:00:00+00:00",
  "updated_at": "2026-02-26T14:00:00+00:00"
}
}
```

**Errors:**
- `404` — Подписка не найдена
- `422` — Ошибка валидации

---

### POST /subscriptions/{id}/pause

Приостанавливает активную подписку. Новые счета не будут выставляться до возобновления.

**Response 200:**
```json
{
  "message": "Subscription paused",
  "subscription": {
  "id": 1,
  "subscriber_name": "Иван Петров",
  "phone_number": "87001234567",
  "external_subscriber_id": "client-42",
  "amount": "5000.00",
  "cart_items": null,
  "description": "Ежемесячная подписка",
  "billing_period": "monthly",
  "billing_period_label": "Ежемесячно",
  "billing_day": 15,
  "billing_day_label": "15 числа",
  "status": "paused",
  "status_label": "Приостановлена",
  "status_color": "yellow",
  "started_at": "2026-01-15T00:00:00+00:00",
  "next_billing_at": null,
  "next_billing_in_days": null,
  "next_billing_label": null,
  "paused_at": "2026-02-26T14:00:00+00:00",
  "cancelled_at": null,
  "failed_attempts": 0,
  "max_retry_attempts": 3,
  "retry_interval_hours": 24,
  "grace_period_days": 3,
  "in_grace_period": false,
  "is_sandbox": false,
  "metadata": null,
  "created_at": "2026-01-10T12:00:00+00:00",
  "updated_at": "2026-02-26T14:00:00+00:00"
}
}
```

**Errors:**
- `400` — Некорректный статус
- `404` — Подписка не найдена

---

### POST /subscriptions/{id}/resume

Возобновляет приостановленную подписку. Следующий биллинг будет рассчитан автоматически.

**Response 200:**
```json
{
  "message": "Subscription resumed",
  "subscription": {
  "id": 1,
  "subscriber_name": "Иван Петров",
  "phone_number": "87001234567",
  "external_subscriber_id": "client-42",
  "amount": "5000.00",
  "cart_items": null,
  "description": "Ежемесячная подписка",
  "billing_period": "monthly",
  "billing_period_label": "Ежемесячно",
  "billing_day": 15,
  "billing_day_label": "15 числа",
  "status": "active",
  "status_label": "Активна",
  "status_color": "green",
  "started_at": "2026-01-15T00:00:00+00:00",
  "next_billing_at": "2026-03-15T00:00:00+00:00",
  "next_billing_in_days": 17,
  "next_billing_label": "через 17 дней",
  "paused_at": null,
  "cancelled_at": null,
  "failed_attempts": 0,
  "max_retry_attempts": 3,
  "retry_interval_hours": 24,
  "grace_period_days": 3,
  "in_grace_period": false,
  "is_sandbox": false,
  "metadata": null,
  "created_at": "2026-01-10T12:00:00+00:00",
  "updated_at": "2026-02-26T14:00:00+00:00"
}
}
```

**Errors:**
- `400` — Некорректный статус
- `404` — Подписка не найдена

---

### POST /subscriptions/{id}/cancel

Безвозвратно отменяет подписку. Новые счета больше не будут выставляться.

**Response 200:**
```json
{
  "message": "Subscription cancelled",
  "subscription": {
  "id": 1,
  "subscriber_name": "Иван Петров",
  "phone_number": "87001234567",
  "external_subscriber_id": "client-42",
  "amount": "5000.00",
  "cart_items": null,
  "description": "Ежемесячная подписка",
  "billing_period": "monthly",
  "billing_period_label": "Ежемесячно",
  "billing_day": 15,
  "billing_day_label": "15 числа",
  "status": "cancelled",
  "status_label": "Отменена",
  "status_color": "red",
  "started_at": "2026-01-15T00:00:00+00:00",
  "next_billing_at": null,
  "next_billing_in_days": null,
  "next_billing_label": null,
  "paused_at": null,
  "cancelled_at": "2026-02-26T14:00:00+00:00",
  "failed_attempts": 0,
  "max_retry_attempts": 3,
  "retry_interval_hours": 24,
  "grace_period_days": 3,
  "in_grace_period": false,
  "is_sandbox": false,
  "metadata": null,
  "created_at": "2026-01-10T12:00:00+00:00",
  "updated_at": "2026-02-26T14:00:00+00:00"
}
}
```

**Errors:**
- `400` — Некорректный статус
- `404` — Подписка не найдена

---

### GET /subscriptions/{id}/invoices

Возвращает пагинированный список всех платежей (счетов) по подписке.

**Query parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| per_page | integer | — | Кол-во на страницу (по умолчанию 20). |

**Response 200:**
```json
{
  "data": [{"id":1,"invoice_id":42,"billing_period_start":"2026-02-01","billing_period_end":"2026-02-28","billing_period_label":"01.02.2026 — 28.02.2026","amount":"5000.00","attempt_number":1,"status":"paid","status_label":"Оплачен","status_color":"green","paid_at":"2026-02-01T10:30:00+00:00","failure_reason":null,"invoice":{"id":42,"kaspi_invoice_id":"INV-123456","status":"paid"},"created_at":"2026-02-01T10:00:00+00:00"}],
  "meta": {
  "current_page": 1,
  "total": 3,
  "per_page": 20
}
}
```

**Errors:**
- `404` — Подписка не найдена

---

### Subscription Statuses

| Status | Description |
|--------|-------------|
| `active` | Active, billing on schedule |
| `paused` | Temporarily paused |
| `cancelled` | Permanently cancelled |
| `completed` | Completed all billing cycles |
| `expired` | Expired after grace period |

### Billing Periods

| Period | Description |
|--------|-------------|
| `daily` | Daily |
| `weekly` | Weekly |
| `biweekly` | Biweekly |
| `monthly` | Monthly |
| `quarterly` | Quarterly |
| `yearly` | Yearly |

---

## Webhooks

Webhooks are configured in the ApiPay.kz dashboard (Settings > Connection).
When creating a webhook, you receive a secret for signature verification (HMAC-SHA256).

### Events

- `invoice.status_changed`
- `invoice.refunded`
- `subscription.payment_succeeded`
- `subscription.payment_failed`
- `subscription.grace_period_started`
- `subscription.expired`
- `webhook.test`

### Payloads

#### `invoice.status_changed`

```json
{
  "event": "invoice.status_changed",
  "invoice": {
    "id": 42,
    "external_order_id": "order_123",
    "amount": "15000.00",
    "subtotal": "16500.00",
    "discount_sum": "1500.00",
    "discount_percentage": "10",
    "status": "paid",
    "description": "Оплата заказа",
    "kaspi_invoice_id": "13234689513",
    "client_name": "Иван Иванов",
    "client_phone": "87071234567",
    "is_sandbox": false,
    "paid_at": "2026-02-12T14:35:00+05:00"
  },
  "source": "My API Key",
  "timestamp": "2026-02-12T14:35:01+05:00"
}
```

#### `invoice.refunded`

```json
{
  "event": "invoice.refunded",
  "refund": {
    "id": 5,
    "amount": "2000.00",
    "status": "completed",
    "reason": "Возврат товара",
    "created_at": "2026-02-12T10:00:00+05:00"
  },
  "invoice": {
    "id": 42,
    "external_order_id": "order_123",
    "amount": "5000.00",
    "subtotal": "5500.00",
    "discount_sum": "500.00",
    "total_refunded": "2000.00",
    "available_for_refund": "3000.00",
    "is_fully_refunded": false,
    "is_sandbox": false,
    "status": "paid",
    "kaspi_invoice_id": "13234689513"
  },
  "source": "My API Key",
  "timestamp": "2026-02-12T10:00:01+05:00"
}
```

#### `subscription.payment_succeeded`

```json
{
  "event": "subscription.payment_succeeded",
  "subscription": {
    "id": 10,
    "external_subscriber_id": "CLIENT-001",
    "phone_number": "87071234567",
    "subscriber_name": "Иван Иванов",
    "amount": "5000.00",
    "billing_period": "monthly",
    "status": "active",
    "next_billing_at": "2026-03-01T00:00:00+05:00",
    "failed_attempts": 0,
    "in_grace_period": false,
    "is_sandbox": false
  },
  "invoice_id": 200,
  "amount": "5000.00",
  "paid_at": "2026-02-01T12:00:00+05:00",
  "source": "My API Key",
  "timestamp": "2026-02-01T12:00:01+05:00"
}
```

#### `subscription.payment_failed`

```json
{
  "event": "subscription.payment_failed",
  "subscription": {
    "id": 10,
    "phone_number": "87071234567",
    "amount": "5000.00",
    "billing_period": "monthly",
    "status": "active",
    "failed_attempts": 2,
    "in_grace_period": false,
    "is_sandbox": false
  },
  "invoice_id": 201,
  "amount": "5000.00",
  "reason": "Invoice expired",
  "attempt_number": 2,
  "source": "My API Key",
  "timestamp": "2026-02-02T12:00:01+05:00"
}
```

#### `subscription.grace_period_started`

```json
{
  "event": "subscription.grace_period_started",
  "subscription": {
    "id": 10,
    "phone_number": "87071234567",
    "amount": "5000.00",
    "status": "active",
    "failed_attempts": 3,
    "in_grace_period": true,
    "is_sandbox": false
  },
  "grace_period_days": 3,
  "expires_at": "2026-02-05T12:00:00+05:00",
  "source": "My API Key",
  "timestamp": "2026-02-02T12:00:01+05:00"
}
```

#### `subscription.expired`

```json
{
  "event": "subscription.expired",
  "subscription": {
    "id": 10,
    "phone_number": "87071234567",
    "amount": "5000.00",
    "status": "expired",
    "next_billing_at": null,
    "failed_attempts": 3,
    "in_grace_period": false,
    "is_sandbox": false
  },
  "source": "My API Key",
  "timestamp": "2026-02-05T12:00:01+05:00"
}
```

### `source` field

All webhook payloads contain a `source` field — the name of the API key that created the resource (invoice or subscription). Can be `null`.

### Retry Policy

- **Subscription webhooks** — Повтор до 3 раз с интервалами 1, 5, 15 минут
- **Invoice webhooks** — Без повторов
- Your server must respond within 10 секунд
- HTTP 2xx = success, any other code = failure

### Signature Verification

Header: `X-Webhook-Signature: sha256=<hex>`

#### JavaScript / Node.js
```javascript
import crypto from 'crypto'

function verifyWebhook(payload, signature, secret) {
  const expected = 'sha256=' + crypto
    .createHmac('sha256', secret)
    .update(payload)
    .digest('hex')
  return crypto.timingSafeEqual(
    Buffer.from(expected),
    Buffer.from(signature)
  )
}

// Usage:
// const signature = req.headers['x-webhook-signature']
// const isValid = verifyWebhook(req.rawBody, signature, webhookSecret)
```

#### PHP
```php
<?php
function verifyWebhook(string $payload, string $signature, string $secret): bool {
    $expected = 'sha256=' . hash_hmac('sha256', $payload, $secret);
    return hash_equals($expected, $signature);
}

// Usage:
// $signature = $_SERVER['HTTP_X_WEBHOOK_SIGNATURE'];
// $payload = file_get_contents('php://input');
// $isValid = verifyWebhook($payload, $signature, $webhookSecret);
```

#### Python
```python
import hmac
import hashlib

def verify_webhook(payload: bytes, signature: str, secret: str) -> bool:
    expected = 'sha256=' + hmac.new(
        secret.encode(),
        payload,
        hashlib.sha256
    ).hexdigest()
    return hmac.compare_digest(expected, signature)

# Usage:
# signature = request.headers.get('X-Webhook-Signature')
# is_valid = verify_webhook(request.body, signature, webhook_secret)
```

---

## Code Examples

### Создание счёта

#### JavaScript / Node.js
```javascript
const response = await fetch('https://bpapi.bazarbay.site/api/v1/invoices', {
  method: 'POST',
  headers: {
    'X-API-Key': 'YOUR_API_KEY',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    amount: 10000,
    phone_number: '87001234567',
    description: 'Payment for order #123'
  })
})

const data = await response.json()
console.log('Invoice created:', data.id)
```

#### Python
```python
import requests

response = requests.post(
    'https://bpapi.bazarbay.site/api/v1/invoices',
    headers={
        'X-API-Key': 'YOUR_API_KEY',
        'Content-Type': 'application/json'
    },
    json={
        'amount': 10000,
        'phone_number': '87001234567',
        'description': 'Payment for order #123'
    }
)

data = response.json()
print(f"Invoice created: {data['id']}")
```

#### PHP
```php
<?php
$ch = curl_init('https://bpapi.bazarbay.site/api/v1/invoices');

curl_setopt_array($ch, [
    CURLOPT_POST => true,
    CURLOPT_HTTPHEADER => [
        'X-API-Key: YOUR_API_KEY',
        'Content-Type: application/json'
    ],
    CURLOPT_POSTFIELDS => json_encode([
        'amount' => 10000,
        'phone_number' => '87001234567',
        'description' => 'Payment for order #123'
    ]),
    CURLOPT_RETURNTRANSFER => true
]);

$response = json_decode(curl_exec($ch), true);
echo "Invoice created: " . $response['id'];
curl_close($ch);
```

#### cURL
```bash
curl -X POST https://bpapi.bazarbay.site/api/v1/invoices \
  -H "X-API-Key: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 10000,
    "phone_number": "87001234567",
    "description": "Payment for order #123"
  }'
```

### Создание счёта с корзиной

#### JavaScript / Node.js
```javascript
const response = await fetch('https://bpapi.bazarbay.site/api/v1/invoices', {
  method: 'POST',
  headers: {
    'X-API-Key': 'YOUR_API_KEY',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    phone_number: '87001234567',
    description: 'Cart order',
    cart_items: [
      { catalog_item_id: 1, count: 2, price: 4500.00 },
      { catalog_item_id: 5, count: 3 }
    ],
    discount_percentage: 10
  })
})
// Response includes subtotal, discount_sum, discount_percentage
```

#### Python
```python
import requests

response = requests.post(
    'https://bpapi.bazarbay.site/api/v1/invoices',
    headers={
        'X-API-Key': 'YOUR_API_KEY',
        'Content-Type': 'application/json'
    },
    json={
        'phone_number': '87001234567',
        'description': 'Cart order',
        'cart_items': [
            {'catalog_item_id': 1, 'count': 2, 'price': 4500.00},
            {'catalog_item_id': 5, 'count': 3}
        ],
        'discount_percentage': 10
    }
)
data = response.json()
```

#### PHP
```php
<?php
$ch = curl_init('https://bpapi.bazarbay.site/api/v1/invoices');

curl_setopt_array($ch, [
    CURLOPT_POST => true,
    CURLOPT_HTTPHEADER => [
        'X-API-Key: YOUR_API_KEY',
        'Content-Type: application/json'
    ],
    CURLOPT_POSTFIELDS => json_encode([
        'phone_number' => '87001234567',
        'description' => 'Cart order',
        'cart_items' => [
            ['catalog_item_id' => 1, 'count' => 2, 'price' => 4500.00],
            ['catalog_item_id' => 5, 'count' => 3]
        ],
        'discount_percentage' => 10
    ]),
    CURLOPT_RETURNTRANSFER => true
]);

$response = json_decode(curl_exec($ch), true);
curl_close($ch);
```

#### cURL
```bash
curl -X POST https://bpapi.bazarbay.site/api/v1/invoices \
  -H "X-API-Key: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "phone_number": "87001234567",
    "description": "Cart order",
    "cart_items": [
      { "catalog_item_id": 1, "count": 2, "price": 4500.00 },
      { "catalog_item_id": 5, "count": 3 }
    ],
    "discount_percentage": 10
  }'
```

### Создание подписки

#### JavaScript / Node.js
```javascript
const response = await fetch('https://bpapi.bazarbay.site/api/v1/subscriptions', {
  method: 'POST',
  headers: {
    'X-API-Key': 'YOUR_API_KEY',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    phone_number: '87001234567',
    amount: 5000,
    billing_period: 'monthly',
    description: 'Monthly subscription'
  })
})
const data = await response.json()
console.log('Subscription:', data.subscription.id)
```

#### Python
```python
import requests

response = requests.post(
    'https://bpapi.bazarbay.site/api/v1/subscriptions',
    headers={
        'X-API-Key': 'YOUR_API_KEY',
        'Content-Type': 'application/json'
    },
    json={
        'phone_number': '87001234567',
        'amount': 5000,
        'billing_period': 'monthly',
        'description': 'Monthly subscription'
    }
)
data = response.json()
print(f"Subscription: {data['subscription']['id']}")
```

#### PHP
```php
<?php
$ch = curl_init('https://bpapi.bazarbay.site/api/v1/subscriptions');

curl_setopt_array($ch, [
    CURLOPT_POST => true,
    CURLOPT_HTTPHEADER => [
        'X-API-Key: YOUR_API_KEY',
        'Content-Type: application/json'
    ],
    CURLOPT_POSTFIELDS => json_encode([
        'phone_number' => '87001234567',
        'amount' => 5000,
        'billing_period' => 'monthly',
        'description' => 'Monthly subscription'
    ]),
    CURLOPT_RETURNTRANSFER => true
]);

$response = json_decode(curl_exec($ch), true);
echo "Subscription: " . $response['subscription']['id'];
curl_close($ch);
```

#### cURL
```bash
curl -X POST https://bpapi.bazarbay.site/api/v1/subscriptions \
  -H "X-API-Key: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "phone_number": "87001234567",
    "amount": 5000,
    "billing_period": "monthly",
    "description": "Monthly subscription"
  }'
```

### Создание подписки с корзиной

#### JavaScript / Node.js
```javascript
const response = await fetch('https://bpapi.bazarbay.site/api/v1/subscriptions', {
  method: 'POST',
  headers: {
    'X-API-Key': 'YOUR_API_KEY',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    phone_number: '87001234567',
    billing_period: 'monthly',
    description: 'Monthly cart subscription',
    cart_items: [
      { catalog_item_id: 1, count: 2 },
      { catalog_item_id: 5, count: 1 }
    ]
  })
})
const data = await response.json()
```

#### Python
```python
import requests

response = requests.post(
    'https://bpapi.bazarbay.site/api/v1/subscriptions',
    headers={
        'X-API-Key': 'YOUR_API_KEY',
        'Content-Type': 'application/json'
    },
    json={
        'phone_number': '87001234567',
        'billing_period': 'monthly',
        'description': 'Monthly cart subscription',
        'cart_items': [
            {'catalog_item_id': 1, 'count': 2},
            {'catalog_item_id': 5, 'count': 1}
        ]
    }
)
data = response.json()
```

#### PHP
```php
<?php
$ch = curl_init('https://bpapi.bazarbay.site/api/v1/subscriptions');

curl_setopt_array($ch, [
    CURLOPT_POST => true,
    CURLOPT_HTTPHEADER => [
        'X-API-Key: YOUR_API_KEY',
        'Content-Type: application/json'
    ],
    CURLOPT_POSTFIELDS => json_encode([
        'phone_number' => '87001234567',
        'billing_period' => 'monthly',
        'description' => 'Monthly cart subscription',
        'cart_items' => [
            ['catalog_item_id' => 1, 'count' => 2],
            ['catalog_item_id' => 5, 'count' => 1]
        ]
    ]),
    CURLOPT_RETURNTRANSFER => true
]);

$response = json_decode(curl_exec($ch), true);
curl_close($ch);
```

#### cURL
```bash
curl -X POST https://bpapi.bazarbay.site/api/v1/subscriptions \
  -H "X-API-Key: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "phone_number": "87001234567",
    "billing_period": "monthly",
    "description": "Monthly cart subscription",
    "cart_items": [
      { "catalog_item_id": 1, "count": 2 },
      { "catalog_item_id": 5, "count": 1 }
    ]
  }'
```

### Загрузка изображения и создание товара

#### JavaScript / Node.js
```javascript
// Step 1: Upload image
const formData = new FormData()
formData.append('image', imageFile)

const uploadRes = await fetch('https://bpapi.bazarbay.site/api/v1/catalog/upload-image', {
  method: 'POST',
  headers: { 'X-API-Key': 'YOUR_API_KEY' },
  body: formData
})
const { image_id } = await uploadRes.json()

// Step 2: Create product with image
const productRes = await fetch('https://bpapi.bazarbay.site/api/v1/catalog', {
  method: 'POST',
  headers: {
    'X-API-Key': 'YOUR_API_KEY',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    items: [{
      name: 'Coffee Latte',
      selling_price: 1800,
      unit_id: 1,
      image_id
    }]
  })
})
```

#### Python
```python
import requests

# Step 1: Upload image
upload_res = requests.post(
    'https://bpapi.bazarbay.site/api/v1/catalog/upload-image',
    headers={'X-API-Key': 'YOUR_API_KEY'},
    files={'image': open('product.jpg', 'rb')}
)
image_id = upload_res.json()['image_id']

# Step 2: Create product with image
product_res = requests.post(
    'https://bpapi.bazarbay.site/api/v1/catalog',
    headers={
        'X-API-Key': 'YOUR_API_KEY',
        'Content-Type': 'application/json'
    },
    json={
        'items': [{
            'name': 'Coffee Latte',
            'selling_price': 1800,
            'unit_id': 1,
            'image_id': image_id
        }]
    }
)
```

#### PHP
```php
<?php
// Step 1: Upload image
$ch = curl_init('https://bpapi.bazarbay.site/api/v1/catalog/upload-image');
$cfile = new CURLFile('product.jpg', 'image/jpeg');
curl_setopt_array($ch, [
    CURLOPT_POST => true,
    CURLOPT_HTTPHEADER => ['X-API-Key: YOUR_API_KEY'],
    CURLOPT_POSTFIELDS => ['image' => $cfile],
    CURLOPT_RETURNTRANSFER => true
]);
$imageId = json_decode(curl_exec($ch), true)['image_id'];
curl_close($ch);

// Step 2: Create product with image
$ch = curl_init('https://bpapi.bazarbay.site/api/v1/catalog');
curl_setopt_array($ch, [
    CURLOPT_POST => true,
    CURLOPT_HTTPHEADER => [
        'X-API-Key: YOUR_API_KEY',
        'Content-Type: application/json'
    ],
    CURLOPT_POSTFIELDS => json_encode([
        'items' => [[
            'name' => 'Coffee Latte',
            'selling_price' => 1800,
            'unit_id' => 1,
            'image_id' => $imageId
        ]]
    ]),
    CURLOPT_RETURNTRANSFER => true
]);
$response = json_decode(curl_exec($ch), true);
curl_close($ch);
```

#### cURL
```bash
# Step 1: Upload image
IMAGE_ID=$(curl -s -X POST https://bpapi.bazarbay.site/api/v1/catalog/upload-image \
  -H "X-API-Key: YOUR_API_KEY" \
  -F "image=@product.jpg" | jq -r '.image_id')

# Step 2: Create product with image
curl -X POST https://bpapi.bazarbay.site/api/v1/catalog \
  -H "X-API-Key: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"items\": [{
      \"name\": \"Coffee Latte\",
      \"selling_price\": 1800,
      \"unit_id\": 1,
      \"image_id\": \"$IMAGE_ID\"
    }]
  }"
```

### Возврат средств

#### JavaScript / Node.js
```javascript
// Full refund
await fetch('https://bpapi.bazarbay.site/api/v1/invoices/42/refund', {
  method: 'POST',
  headers: {
    'X-API-Key': 'YOUR_API_KEY',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({ reason: 'Customer request' })
})

// Partial refund
await fetch('https://bpapi.bazarbay.site/api/v1/invoices/42/refund', {
  method: 'POST',
  headers: {
    'X-API-Key': 'YOUR_API_KEY',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({ amount: 5000, reason: 'Partial return' })
})
```

#### Python
```python
import requests

# Full refund
requests.post(
    'https://bpapi.bazarbay.site/api/v1/invoices/42/refund',
    headers={
        'X-API-Key': 'YOUR_API_KEY',
        'Content-Type': 'application/json'
    },
    json={'reason': 'Customer request'}
)

# Partial refund
requests.post(
    'https://bpapi.bazarbay.site/api/v1/invoices/42/refund',
    headers={
        'X-API-Key': 'YOUR_API_KEY',
        'Content-Type': 'application/json'
    },
    json={'amount': 5000, 'reason': 'Partial return'}
)
```

#### PHP
```php
<?php
// Full refund
$ch = curl_init('https://bpapi.bazarbay.site/api/v1/invoices/42/refund');
curl_setopt_array($ch, [
    CURLOPT_POST => true,
    CURLOPT_HTTPHEADER => [
        'X-API-Key: YOUR_API_KEY',
        'Content-Type: application/json'
    ],
    CURLOPT_POSTFIELDS => json_encode([
        'reason' => 'Customer request'
    ]),
    CURLOPT_RETURNTRANSFER => true
]);
curl_exec($ch);
curl_close($ch);

// Partial refund
$ch = curl_init('https://bpapi.bazarbay.site/api/v1/invoices/42/refund');
curl_setopt_array($ch, [
    CURLOPT_POST => true,
    CURLOPT_HTTPHEADER => [
        'X-API-Key: YOUR_API_KEY',
        'Content-Type: application/json'
    ],
    CURLOPT_POSTFIELDS => json_encode([
        'amount' => 5000,
        'reason' => 'Partial return'
    ]),
    CURLOPT_RETURNTRANSFER => true
]);
curl_exec($ch);
curl_close($ch);
```

#### cURL
```bash
# Full refund
curl -X POST https://bpapi.bazarbay.site/api/v1/invoices/42/refund \
  -H "X-API-Key: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"reason": "Customer request"}'

# Partial refund
curl -X POST https://bpapi.bazarbay.site/api/v1/invoices/42/refund \
  -H "X-API-Key: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"amount": 5000, "reason": "Partial return"}'
```

> Examples cover the most common integration scenarios. Each endpoint is fully documented above.

---

## Error Response Format

### Validation (422)

```json
{
  "message": "The given data was invalid.",
  "errors": {
    "phone_number": ["The phone number field is required."],
    "amount": ["The amount must be between 0.01 and 99999999.99."]
  }
}
```

### Not_found (404)

```json
{
  "message": "Invoice not found"
}
```

### Unauthorized (401)

```json
{
  "message": "Invalid or missing API key"
}
```

---

## Error Codes

| Code | Description |
|------|-------------|
| 400 | Bad Request — некорректный запрос или состояние |
| 401 | Unauthorized — неверный, отсутствующий или истёкший API ключ |
| 403 | Forbidden — организация не верифицирована |
| 404 | Not Found — ресурс не найден |
| 422 | Validation Error — ошибка валидации полей |
| 429 | Too Many Requests — превышен rate limit (проверьте retry_after) |
| 500 | Server Error — ошибка сервера |
| 502 | Bad Gateway — ошибка Kaspi API |
| 503 | Service Unavailable — Kaspi сессия истекла |


---

## Changelog

- **2026-04-07** [NEW] Статус processing — счёт создан и ожидает отправки в Kaspi
- **2026-04-07** [NEW] Статус error и поле error_message — описание ошибки при сбое отправки
- **2026-04-07** [NEW] Параметр discount_percentage — скидка на весь чек (1-99%)
- **2026-04-07** [NEW] Параметр bill_immediately — выставить первый счёт подписки сразу
- **2026-04-07** [CHANGED] POST /invoices/{id}/cancel — теперь работает для статусов pending и processing
- **2026-04-07** [NEW] Ошибки Kaspi-сессии: 400 kaspi_session_not_configured, 503 kaspi_session_invalid
- **2026-04-07** [CHANGED] POST /invoices/status/check — обновлён формат ответа: { invoices: [...] }
- **2026-04-02** [CHANGED] Обновлена API спецификация: удалён GET /status, добавлены новые поля в каталоге (kaspi_item_id, nds_percentage, ntin, synced_at и др.), подписках (billing_period_label, status_label, status_color, paused_at, cancelled_at и др.) и возвратах (organization_id, updated_at), обновлён формат пагинации
- **2026-03-29** [NEW] Добавлен параметр bill_immediately в POST /subscriptions — немедленное выставление первого счёта при создании подписки
- **2026-03-29** [NEW] Добавлено поле created_at в ответы эндпоинтов каталога (GET /catalog, POST /catalog) — дата создания товара в системе в формате ISO 8601
- **2026-03-27** [NEW] Запуск публичной API документации с единым источником правды