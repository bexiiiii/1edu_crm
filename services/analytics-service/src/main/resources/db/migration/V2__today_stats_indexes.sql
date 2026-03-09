-- ============================================================
-- Analytics Service V2 — индексы и колонки для today-stats
-- ============================================================

-- ── students: birth_date для дней рождения ───────────────────
ALTER TABLE students ADD COLUMN IF NOT EXISTS birth_date DATE;

-- EXTRACT(MONTH/DAY FROM date) is IMMUTABLE — safe for index expressions
CREATE INDEX IF NOT EXISTS idx_students_birth_mmdd
    ON students ((EXTRACT(MONTH FROM birth_date)::int), (EXTRACT(DAY FROM birth_date)::int))
    WHERE birth_date IS NOT NULL AND status = 'ACTIVE';

-- ── students: полное имя (часто нужно в аналитике) ───────────
CREATE INDEX IF NOT EXISTS idx_students_status
    ON students (status);

-- ── transactions: быстрый поиск по дате и типу ───────────────
CREATE INDEX IF NOT EXISTS idx_transactions_date_type_status
    ON transactions (transaction_date, type, status);

-- ── subscriptions: статус + дата начала ──────────────────────
CREATE INDEX IF NOT EXISTS idx_subscriptions_status_start
    ON subscriptions (status, start_date);

CREATE INDEX IF NOT EXISTS idx_subscriptions_status_end
    ON subscriptions (status, end_date)
    WHERE end_date IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_subscriptions_lessons_left
    ON subscriptions (lessons_left)
    WHERE status = 'ACTIVE';

-- ── lessons: быстрый поиск занятий за дату ───────────────────
CREATE INDEX IF NOT EXISTS idx_lessons_date_status
    ON lessons (lesson_date, status);

-- ── attendances: статус + student ────────────────────────────
CREATE INDEX IF NOT EXISTS idx_attendances_status_student
    ON attendances (status, student_id);

-- ── student_groups: дата записи ──────────────────────────────
-- V1 already indexes enrolled_at (TIMESTAMPTZ) directly.
-- A DATE(enrolled_at) expression index is not possible because
-- timestamptz→date cast is STABLE (depends on session timezone).
-- Range queries like WHERE enrolled_at >= '2024-01-01' AND enrolled_at < '2024-01-02'
-- will use the existing idx_student_groups_enrolled index.
