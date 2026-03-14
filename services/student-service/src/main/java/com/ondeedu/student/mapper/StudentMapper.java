package com.ondeedu.student.mapper;

import com.ondeedu.student.dto.CreateStudentRequest;
import com.ondeedu.student.dto.StudentDto;
import com.ondeedu.student.dto.UpdateStudentRequest;
import com.ondeedu.student.entity.Student;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface StudentMapper {

    @Mapping(target = "fullName", expression = "java(student.getFullName())")
    @Mapping(target = "additionalPhones", ignore = true)
    @Mapping(target = "stateOrderParticipant", ignore = true)
    @Mapping(target = "loyalty", ignore = true)
    StudentDto toDto(Student student);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    Student toEntity(CreateStudentRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    void updateEntity(@MappingTarget Student student, UpdateStudentRequest request);
}
