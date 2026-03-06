-- ============================================================
-- Analytics Service V2 — индексы и колонки для today-stats
-- ============================================================

-- ── students: birth_date для дней рождения ───────────────────
ALTER TABLE students ADD COLUMN IF NOT EXISTS birth_date DATE;

CREATE INDEX IF NOT EXISTS idx_students_birth_mmdd
    ON students (TO_CHAR(birth_date, 'MM-DD'))
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
CREATE INDEX IF NOT EXISTS idx_student_groups_enrolled_date
    ON student_groups ((DATE(enrolled_at AT TIME ZONE 'UTC')));
