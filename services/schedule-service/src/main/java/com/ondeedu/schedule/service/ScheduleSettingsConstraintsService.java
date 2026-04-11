package com.ondeedu.schedule.service;

import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.schedule.entity.Schedule;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Time;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ScheduleSettingsConstraintsService {

    private final JdbcTemplate jdbcTemplate;

    public void validate(Schedule schedule) {
        TenantScheduleSettings settings = loadSettings();

        validateTeacherIsActive(schedule.getTeacherId());
        validateTimeRange(schedule, settings);
        validateWorkingDays(schedule, settings);
        validateMaxGroupSize(schedule, settings);
    }

    private void validateTeacherIsActive(UUID teacherId) {
        if (teacherId == null) {
            return;
        }

        String status = jdbcTemplate.query(
                "SELECT status FROM staff WHERE id = ?",
                rs -> rs.next() ? rs.getString("status") : null,
                teacherId
        );

        if (!"ACTIVE".equals(status)) {
            throw new BusinessException(
                    "SCHEDULE_TEACHER_NOT_ACTIVE",
                    "Selected teacher is not active and cannot be assigned to schedule"
            );
        }
    }

    private void validateTimeRange(Schedule schedule, TenantScheduleSettings settings) {
        LocalTime start = schedule.getStartTime();
        LocalTime end = schedule.getEndTime();
        if (start == null || end == null) {
            return;
        }

        if (!end.isAfter(start)) {
            throw new BusinessException(
                    "INVALID_SCHEDULE_TIME_RANGE",
                    "Schedule endTime must be greater than startTime"
            );
        }

        if (start.isBefore(settings.workingHoursStart()) || end.isAfter(settings.workingHoursEnd())) {
            throw new BusinessException(
                    "SCHEDULE_OUTSIDE_WORKING_HOURS",
                    "Schedule time must be inside tenant working hours: "
                            + settings.workingHoursStart() + " - " + settings.workingHoursEnd()
            );
        }

        int slotDuration = settings.slotDurationMin();
        if (slotDuration > 0) {
            long minutes = Duration.between(start, end).toMinutes();
            if (minutes % slotDuration != 0) {
                throw new BusinessException(
                        "SCHEDULE_SLOT_DURATION_VIOLATION",
                        "Lesson duration must be a multiple of slot_duration_min=" + slotDuration
                );
            }
        }
    }

    private void validateWorkingDays(Schedule schedule, TenantScheduleSettings settings) {
        Set<DayOfWeek> requested = schedule.getDaysOfWeek();
        if (requested == null || requested.isEmpty()) {
            return;
        }

        if (!settings.workingDays().containsAll(requested)) {
            throw new BusinessException(
                    "SCHEDULE_OUTSIDE_WORKING_DAYS",
                    "Schedule daysOfWeek must be inside tenant workingDays"
            );
        }
    }

    private void validateMaxGroupSize(Schedule schedule, TenantScheduleSettings settings) {
        Integer maxStudents = schedule.getMaxStudents();
        if (maxStudents == null) {
            return;
        }

        if (maxStudents > settings.maxGroupSize()) {
            throw new BusinessException(
                    "SCHEDULE_MAX_GROUP_SIZE_EXCEEDED",
                    "Max students exceeds tenant settings max_group_size: " + settings.maxGroupSize()
            );
        }
    }

    private TenantScheduleSettings loadSettings() {
        try {
            return jdbcTemplate.query(
                    """
                    SELECT working_hours_start,
                           working_hours_end,
                           slot_duration_min,
                           working_days,
                           max_group_size
                    FROM tenant_settings
                    ORDER BY created_at ASC
                    LIMIT 1
                    """,
                    rs -> {
                        if (!rs.next()) {
                            return TenantScheduleSettings.defaults();
                        }

                        Time start = rs.getTime("working_hours_start");
                        Time end = rs.getTime("working_hours_end");
                        Integer slot = (Integer) rs.getObject("slot_duration_min");
                        Integer maxGroupSize = (Integer) rs.getObject("max_group_size");

                        return new TenantScheduleSettings(
                                start != null ? start.toLocalTime() : LocalTime.of(9, 0),
                                end != null ? end.toLocalTime() : LocalTime.of(21, 0),
                                slot != null && slot > 0 ? slot : 30,
                                parseWorkingDays(rs.getString("working_days")),
                                maxGroupSize != null && maxGroupSize > 0 ? maxGroupSize : 20
                        );
                    }
            );
        } catch (Exception ignored) {
            return TenantScheduleSettings.defaults();
        }
    }

    private Set<DayOfWeek> parseWorkingDays(String raw) {
        if (raw == null || raw.isBlank()) {
            return TenantScheduleSettings.defaults().workingDays();
        }

        Set<DayOfWeek> parsed = EnumSet.noneOf(DayOfWeek.class);
        Arrays.stream(raw.replace("[", "")
                        .replace("]", "")
                        .replace("\"", "")
                        .split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .forEach(token -> {
                    try {
                        parsed.add(DayOfWeek.valueOf(token.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ignored) {
                        // Ignore invalid tokens and fall back to defaults if needed.
                    }
                });

        return parsed.isEmpty() ? TenantScheduleSettings.defaults().workingDays() : parsed;
    }

    private record TenantScheduleSettings(
            LocalTime workingHoursStart,
            LocalTime workingHoursEnd,
            int slotDurationMin,
            Set<DayOfWeek> workingDays,
            int maxGroupSize
    ) {
        static TenantScheduleSettings defaults() {
            return new TenantScheduleSettings(
                    LocalTime.of(9, 0),
                    LocalTime.of(21, 0),
                    30,
                    EnumSet.of(
                            DayOfWeek.MONDAY,
                            DayOfWeek.TUESDAY,
                            DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY,
                            DayOfWeek.FRIDAY,
                            DayOfWeek.SATURDAY
                    ),
                    20
            );
        }
    }
}
