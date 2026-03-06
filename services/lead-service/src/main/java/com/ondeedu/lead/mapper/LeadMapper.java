package com.ondeedu.lead.mapper;

import com.ondeedu.lead.dto.CreateLeadRequest;
import com.ondeedu.lead.dto.LeadDto;
import com.ondeedu.lead.dto.UpdateLeadRequest;
import com.ondeedu.lead.entity.Lead;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface LeadMapper {

    @Mapping(target = "fullName", expression = "java(lead.getFullName())")
    LeadDto toDto(Lead lead);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "stage", constant = "NEW")
    @Mapping(target = "assignedTo", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    Lead toEntity(CreateLeadRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(@MappingTarget Lead lead, UpdateLeadRequest request);
}
