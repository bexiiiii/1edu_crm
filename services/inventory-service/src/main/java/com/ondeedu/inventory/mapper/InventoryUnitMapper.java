package com.ondeedu.inventory.mapper;

import com.ondeedu.inventory.dto.InventoryUnitDto;
import com.ondeedu.inventory.dto.SaveInventoryUnitRequest;
import com.ondeedu.inventory.entity.InventoryUnit;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface InventoryUnitMapper {

    InventoryUnitDto toDto(InventoryUnit entity);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "branchId", ignore = true)
    @Mapping(target = "isSystem", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    InventoryUnit toEntity(SaveInventoryUnitRequest request);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "branchId", ignore = true)
    @Mapping(target = "isSystem", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    void updateEntity(@MappingTarget InventoryUnit entity, SaveInventoryUnitRequest request);
}
