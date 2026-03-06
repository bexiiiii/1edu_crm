package com.ondeedu.lesson.entity;

public enum AttendanceStatus {
    PLANNED,        // до урока (по умолчанию)
    ATTENDED,       // Посетил(а)
    ABSENT,         // Пропустил(а)
    SICK,           // Болел
    VACATION,       // Отпуск
    AUTO_ATTENDED,  // Посетил (авто)
    ONE_TIME_VISIT  // Разовый урок
}
