package com.ondeedu.inventory.mapper;

import com.ondeedu.inventory.dto.CreateInventoryItemRequest;
import com.ondeedu.inventory.dto.InventoryItemDto;
import com.ondeedu.inventory.dto.UpdateInventoryItemRequest;
import com.ondeedu.inventory.entity.InventoryItem;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface InventoryItemMapper {

    @Mapping(target = "categoryId", source = "entity.category.id")
    @Mapping(target = "categoryName", source = "entity.category.name")
    @Mapping(target = "unitId", source = "entity.unit.id")
    @Mapping(target = "unitName", source = "entity.unit.name")
    @Mapping(target = "unitAbbreviation", source = "entity.unit.abbreviation")
    @Mapping(target = "totalValue", expression = "java(entity.calculateTotalValue())")
    InventoryItemDto toDto(InventoryItem entity);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "branchId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "requiresReorder", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "unit", ignore = true)
    InventoryItem toEntity(CreateInventoryItemRequest request);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "branchId", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "unit", ignore = true)
    void updateEntity(@MappingTarget InventoryItem entity, UpdateInventoryItemRequest request);
}
