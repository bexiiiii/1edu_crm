package com.ondeedu.staff.mapper;

import com.ondeedu.staff.dto.CreateStaffRequest;
import com.ondeedu.staff.dto.StaffDto;
import com.ondeedu.staff.dto.UpdateStaffRequest;
import com.ondeedu.staff.entity.Staff;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface StaffMapper {

    @Mapping(target = "fullName", expression = "java(staff.getFullName())")
    StaffDto toDto(Staff staff);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    Staff toEntity(CreateStaffRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(@MappingTarget Staff staff, UpdateStaffRequest request);
}
