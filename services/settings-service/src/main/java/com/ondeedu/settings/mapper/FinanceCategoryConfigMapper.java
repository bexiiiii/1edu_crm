package com.ondeedu.settings.mapper;

import com.ondeedu.settings.dto.FinanceCategoryConfigDto;
import com.ondeedu.settings.dto.SaveFinanceCategoryRequest;
import com.ondeedu.settings.entity.FinanceCategoryConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface FinanceCategoryConfigMapper {

    FinanceCategoryConfigDto toDto(FinanceCategoryConfig entity);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    FinanceCategoryConfig toEntity(SaveFinanceCategoryRequest request);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(@MappingTarget FinanceCategoryConfig entity, SaveFinanceCategoryRequest request);
}
