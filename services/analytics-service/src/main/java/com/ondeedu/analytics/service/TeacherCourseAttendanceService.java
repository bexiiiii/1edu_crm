package com.ondeedu.analytics.service;

import com.ondeedu.analytics.config.AnalyticsCacheNames;
import com.ondeedu.analytics.dto.response.TeacherCourseAttendanceResponse;
import com.ondeedu.analytics.dto.response.TeacherCourseAttendanceResponse.AttendanceCellDto;
import com.ondeedu.analytics.dto.response.TeacherCourseAttendanceResponse.LessonDayDto;
import com.ondeedu.analytics.dto.response.TeacherCourseAttendanceResponse.StudentAttendanceRow;
import com.ondeedu.analytics.dto.response.TeacherCourseAttendanceResponse.TeacherCourseDto;
import com.ondeedu.analytics.repository.AnalyticsRepository;
import com.ondeedu.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherCourseAttendanceService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    // statuses that count as "attended"
    private static final Set<String> ATTENDED_STATUSES = Set.of("ATTENDED", "AUTO_ATTENDED");

    private final AnalyticsRepository repo;

    // ── Дропдаун: курсы учителя ───────────────────────────────────────────────

    @Cacheable(value = AnalyticsCacheNames.GROUP_ATTENDANCE, keyGenerator = "tenantCacheKeyGenerator")
    @Transactional(readOnly = true)
    public List<TeacherCourseDto> getTeacherCourses(UUID teacherId) {
        String schema = TenantContext.getSchemaName();
        List<Map<String, Object>> rows = repo.getTeacherCourses(schema, teacherId);
        return rows.stream()
                .map(r -> TeacherCourseDto.builder()
                        .id((UUID) r.get("id"))
                        .name((String) r.get("name"))
                        .status((String) r.get("status"))
                        .build())
                .toList();
    }

    // ── Pivot таблица посещаемости ────────────────────────────────────────────

    @Cacheable(value = AnalyticsCacheNames.GROUP_ATTENDANCE, keyGenerator = "tenantCacheKeyGenerator")
    @Transactional(readOnly = true)
    public TeacherCourseAttendanceResponse getTeacherCourseAttendance(UUID teacherId, UUID courseId, String month) {
        String schema = TenantContext.getSchemaName();
        YearMonth yearMonth = parseMonth(month);
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();

        // 1. Информация об учителе и курсе
        String teacherName = resolveTeacherName(schema, teacherId);
        String courseName = resolveCourseName(schema, courseId);

        // 2. Колонки таблицы — занятия месяца
        List<Map<String, Object>> lessonRows = repo.getTeacherCourseLessonDays(schema, teacherId, courseId, monthStart, monthEnd);
        List<LessonDayDto> lessonDays = lessonRows.stream()
                .map(r -> LessonDayDto.builder()
                        .lessonId((UUID) r.get("lesson_id"))
                        .date((String) r.get("lesson_date"))
                        .dayNumber(((Number) r.get("day_number")).intValue())
                        .dayOfWeek((String) r.get("day_of_week"))
                        .build())
                .toList();

        int totalLessons = lessonDays.size();

        // Порядок lessonId для быстрой сборки строк
        List<UUID> lessonIdOrder = lessonDays.stream().map(LessonDayDto::getLessonId).toList();
        Map<UUID, String> lessonIdToDate = lessonDays.stream()
                .collect(Collectors.toMap(LessonDayDto::getLessonId, LessonDayDto::getDate));

        // 3. Сырые данные посещаемости (студент × занятие)
        List<Map<String, Object>> pivotRows = totalLessons == 0
                ? List.of()
                : repo.getTeacherCourseAttendancePivot(schema, teacherId, courseId, monthStart, monthEnd);

        // 4. Группируем по студенту, сохраняя порядок (LinkedHashMap)
        Map<UUID, List<Map<String, Object>>> byStudent = new LinkedHashMap<>();
        Map<UUID, String> studentNames = new LinkedHashMap<>();
        Map<UUID, String> studentStatuses = new LinkedHashMap<>();

        for (Map<String, Object> row : pivotRows) {
            UUID studentId = (UUID) row.get("student_id");
            byStudent.computeIfAbsent(studentId, k -> new ArrayList<>()).add(row);
            studentNames.putIfAbsent(studentId, (String) row.get("student_name"));
            studentStatuses.putIfAbsent(studentId, (String) row.get("student_status"));
        }

        // 5. Строим строки таблицы
        Set<UUID> lessonIdSet = new LinkedHashSet<>(lessonIdOrder);
        List<StudentAttendanceRow> studentRows = new ArrayList<>();
        double totalAttendanceSum = 0;
        int rowsWithLessons = 0;

        for (Map.Entry<UUID, List<Map<String, Object>>> entry : byStudent.entrySet()) {
            UUID studentId = entry.getKey();

            // map lessonId -> status для данного студента
            Map<UUID, String> lessonStatusMap = entry.getValue().stream()
                    .collect(Collectors.toMap(
                            r -> (UUID) r.get("lesson_id"),
                            r -> (String) r.get("attendance_status"),
                            (a, b) -> a  // на случай дубликатов
                    ));

            // Ячейки — ровно в порядке lessonIdOrder
            List<AttendanceCellDto> cells = lessonIdOrder.stream()
                    .map(lid -> AttendanceCellDto.builder()
                            .lessonId(lid)
                            .date(lessonIdToDate.get(lid))
                            .status(lessonStatusMap.getOrDefault(lid, "NOT_MARKED"))
                            .build())
                    .toList();

            int attended = (int) cells.stream()
                    .filter(c -> ATTENDED_STATUSES.contains(c.getStatus()))
                    .count();
            int marked = (int) cells.stream()
                    .filter(c -> !"NOT_MARKED".equals(c.getStatus()))
                    .count();
            int rhythm = totalLessons > 0 ? (int) Math.round(100.0 * attended / totalLessons) : 0;

            studentRows.add(StudentAttendanceRow.builder()
                    .studentId(studentId)
                    .studentName(studentNames.get(studentId))
                    .studentStatus(studentStatuses.get(studentId))
                    .attendance(cells)
                    .attendedCount(attended)
                    .markedCount(marked)
                    .totalLessons(totalLessons)
                    .rhythmPercent(rhythm)
                    .build());

            if (totalLessons > 0) {
                totalAttendanceSum += rhythm;
                rowsWithLessons++;
            }
        }

        double avgAttendanceRate = rowsWithLessons > 0
                ? Math.round(totalAttendanceSum / rowsWithLessons * 10) / 10.0
                : 0.0;

        return TeacherCourseAttendanceResponse.builder()
                .teacherId(teacherId)
                .teacherName(teacherName)
                .courseId(courseId)
                .courseName(courseName)
                .month(yearMonth.format(MONTH_FMT))
                .lessonDays(lessonDays)
                .students(studentRows)
                .totalLessons(totalLessons)
                .avgAttendanceRate(avgAttendanceRate)
                .build();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String resolveTeacherName(String schema, UUID teacherId) {
        try {
            Map<String, Object> info = repo.getTeacherInfo(schema, teacherId);
            return (String) info.get("full_name");
        } catch (Exception e) {
            log.warn("Teacher not found: {}", teacherId);
            return "Unknown";
        }
    }

    private String resolveCourseName(String schema, UUID courseId) {
        try {
            Map<String, Object> info = repo.getCourseInfo(schema, courseId);
            return (String) info.get("name");
        } catch (Exception e) {
            log.warn("Course not found: {}", courseId);
            return "Unknown";
        }
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) return YearMonth.now();
        try {
            return YearMonth.parse(month.trim(), MONTH_FMT);
        } catch (Exception e) {
            return YearMonth.now();
        }
    }
}
