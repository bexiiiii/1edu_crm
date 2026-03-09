-- ============================================================
-- Analytics Service V2 — индексы и колонки для today-stats
-- Индексы создаются только если таблицы уже существуют
-- (таблицы других сервисов могут отсутствовать на этой схеме)
-- ============================================================

-- ── students: birth_date для дней рождения ───────────────────
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'students') THEN
    ALTER TABLE students ADD COLUMN IF NOT EXISTS birth_date DATE;

    CREATE INDEX IF NOT EXISTS idx_students_birth_mmdd
        ON students ((EXTRACT(MONTH FROM birth_date)::int), (EXTRACT(DAY FROM birth_date)::int))
        WHERE birth_date IS NOT NULL AND status = 'ACTIVE';

    CREATE INDEX IF NOT EXISTS idx_students_status
        ON students (status);
  END IF;
END $$;

-- ── transactions: быстрый поиск по дате и типу ───────────────
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'transactions') THEN
    CREATE INDEX IF NOT EXISTS idx_transactions_date_type_status
        ON transactions (transaction_date, type, status);
  END IF;
END $$;

-- ── subscriptions: статус + дата начала ──────────────────────
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'subscriptions') THEN
    CREATE INDEX IF NOT EXISTS idx_subscriptions_status_start
        ON subscriptions (status, start_date);

    CREATE INDEX IF NOT EXISTS idx_subscriptions_status_end
        ON subscriptions (status, end_date)
        WHERE end_date IS NOT NULL;

    CREATE INDEX IF NOT EXISTS idx_subscriptions_lessons_left
        ON subscriptions (lessons_left)
        WHERE status = 'ACTIVE';
  END IF;
END $$;

-- ── lessons: быстрый поиск занятий за дату ───────────────────
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'lessons') THEN
    CREATE INDEX IF NOT EXISTS idx_lessons_date_status
        ON lessons (lesson_date, status);
  END IF;
END $$;

-- ── attendances: статус + student ────────────────────────────
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'attendances') THEN
    CREATE INDEX IF NOT EXISTS idx_attendances_status_student
        ON attendances (status, student_id);
  END IF;
END $$;

-- ── student_groups: дата записи ──────────────────────────────
-- V1 already indexes enrolled_at (TIMESTAMPTZ) directly.
-- A DATE(enrolled_at) expression index is not possible because
-- timestamptz→date cast is STABLE (depends on session timezone).
-- Range queries like WHERE enrolled_at >= '2024-01-01' AND enrolled_at < '2024-01-02'
-- will use the existing idx_student_groups_enrolled index.
