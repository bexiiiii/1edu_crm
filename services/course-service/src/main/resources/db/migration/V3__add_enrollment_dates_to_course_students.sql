-- Add enrollment tracking dates to course_students
ALTER TABLE course_students
    ADD COLUMN IF NOT EXISTS enrolled_at TIMESTAMPTZ;

UPDATE course_students
SET enrolled_at = created_at
WHERE enrolled_at IS NULL;

ALTER TABLE course_students
    ALTER COLUMN enrolled_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN enrolled_at SET NOT NULL;

ALTER TABLE course_students
    ADD COLUMN IF NOT EXISTS removed_at TIMESTAMPTZ;

-- Replace full unique constraint with partial (active enrollments only),
-- allowing re-enrollment history rows.
ALTER TABLE course_students
    DROP CONSTRAINT IF EXISTS uk_course_students_course_student;

CREATE UNIQUE INDEX IF NOT EXISTS uk_course_students_active
    ON course_students (course_id, student_id)
    WHERE removed_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_course_students_student_enrolled
    ON course_students (student_id, enrolled_at DESC);
