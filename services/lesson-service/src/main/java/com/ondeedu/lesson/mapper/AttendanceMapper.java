package com.ondeedu.lesson.mapper;

import com.ondeedu.lesson.dto.AttendanceDto;
import com.ondeedu.lesson.dto.MarkAttendanceRequest;
import com.ondeedu.lesson.entity.Attendance;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface AttendanceMapper {

    AttendanceDto toDto(Attendance attendance);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "lessonId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    Attendance toEntity(MarkAttendanceRequest request);
}
