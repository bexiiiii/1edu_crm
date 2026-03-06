package com.ondeedu.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DashboardResponse {

    // Посещаемость
    private double attendanceRate;
    private double attendancePrevRate;

    // Загрузка групп
    private double groupLoadRate;
    private double groupLoadPrevRate;

    // Конверсия пробных
    private long trialScheduled;
    private long trialAttended;
    private double trialConversionRate;
    private double trialConversionPrevRate;

    // Средний чек / ARPU
    private BigDecimal averageCheck;
    private BigDecimal arpu;
    private BigDecimal arpuPrev;

    // Проданные абонементы
    private long subscriptionsSold;
    private long subscriptionsSoldPrev;
    private double subscriptionsDeltaPct;

    // Динамика клиентов
    private long studentsAtStart;
    private long studentsJoined;
    private double studentsJoinedDeltaPct;
    private long studentsLeft;
    private double studentsLeftDeltaPct;
    private long studentsAtEnd;
    private long studentsDelta;
    private double studentsDeltaPct;

    // Активные ученики по типу занятий (GROUP, INDIVIDUAL)
    private long activeGroupStudents;
    private long activeIndividualStudents;

    // Лучший сотрудник
    private TopEmployeeDto topEmployee;

    // Финансовые показатели
    private BigDecimal revenue;
    private double revenueDeltaPct;
    private BigDecimal expenses;
    private BigDecimal profit;

    // Лиды / договоры
    private long leadsTotal;
    private double leadsDeltaPct;
    private long contractsTotal;
    private double leadsToContractsConversion;

    // Среднее удержание M+1
    private double retentionM1Rate;
    private double retentionM1Delta;

    // Посещаемость по месяцам (для графика)
    private List<MonthlyAttendanceDto> monthlyAttendance;
    private double currentMonthAttendance;
    private double currentMonthAttendanceDelta;

    // Списки пришли/ушли
    private List<StudentMovementDto> joinedStudents;
    private List<StudentMovementDto> leftStudents;
}
