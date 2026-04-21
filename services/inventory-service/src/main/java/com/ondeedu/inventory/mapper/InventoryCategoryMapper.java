package com.ondeedu.inventory.mapper;

import com.ondeedu.inventory.dto.InventoryCategoryDto;
import com.ondeedu.inventory.entity.InventoryCategory;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface InventoryCategoryMapper {

    InventoryCategoryDto toDto(InventoryCategory entity);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "branchId", ignore = true)
    @Mapping(target = "isSystem", ignore = true)
    InventoryCategory toEntity(InventoryCategoryDto dto);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "branchId", ignore = true)
    @Mapping(target = "isSystem", ignore = true)
    void updateEntity(@MappingTarget InventoryCategory entity, InventoryCategoryDto dto);
}
