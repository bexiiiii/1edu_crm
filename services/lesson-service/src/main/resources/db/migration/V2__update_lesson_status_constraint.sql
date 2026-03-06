-- Add TEACHER_ABSENT and TEACHER_SICK statuses to lessons
ALTER TABLE lessons DROP CONSTRAINT IF EXISTS chk_lesson_status;
ALTER TABLE lessons ADD CONSTRAINT chk_lesson_status
    CHECK (status IN ('PLANNED', 'COMPLETED', 'CANCELLED', 'TEACHER_ABSENT', 'TEACHER_SICK'));
