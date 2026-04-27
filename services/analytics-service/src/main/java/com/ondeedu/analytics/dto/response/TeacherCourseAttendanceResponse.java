package com.ondeedu.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class TeacherCourseAttendanceResponse {
    private UUID teacherId;
    private String teacherName;
    private UUID courseId;
    private String courseName;
    private String month;

    /** Колонки таблицы — дни с занятиями, упорядочены по дате */
    private List<LessonDayDto> lessonDays;

    /** Строки таблицы — один студент со списком меток */
    private List<StudentAttendanceRow> students;

    private int totalLessons;
    private double avgAttendanceRate;

    /** Один столбец (одно занятие) */
    @Data
    @Builder
    public static class LessonDayDto {
        private UUID lessonId;
        private String date;        // YYYY-MM-DD
        private int dayNumber;      // 11, 14, 16 …
        private String dayOfWeek;   // ПН, ВТ, СР, ЧТ, ПТ, СБ, ВС
    }

    /** Одна строка — один ученик */
    @Data
    @Builder
    public static class StudentAttendanceRow {
        private UUID studentId;
        private String studentName;
        private String studentStatus;   // ACTIVE, INACTIVE …

        /** Ячейки строки — по одной на каждое занятие из lessonDays (порядок совпадает) */
        private List<AttendanceCellDto> attendance;

        private int attendedCount;   // фактически посетил (ATTENDED + AUTO_ATTENDED)
        private int markedCount;     // отмечено хоть как (не NOT_MARKED)
        private int totalLessons;    // всего занятий в месяце
        private int rhythmPercent;   // attendedCount / totalLessons * 100
    }

    /** Одна ячейка таблицы */
    @Data
    @Builder
    public static class AttendanceCellDto {
        private UUID lessonId;
        private String date;
        private String status;  // ATTENDED, ABSENT, PLANNED, NOT_MARKED, AUTO_ATTENDED, VACATION, SICK, TRIAL …
    }

    /** Элемент дропдауна курсов учителя */
    @Data
    @Builder
    public static class TeacherCourseDto {
        private UUID id;
        private String name;
        private String status;
    }
}
