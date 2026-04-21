package com.ondeedu.inventory.mapper;

import com.ondeedu.inventory.dto.InventoryTransactionDto;
import com.ondeedu.inventory.entity.InventoryTransaction;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface InventoryTransactionMapper {

    @Mapping(target = "transactionType", expression = "java(entity.getTransactionType().name())")
    InventoryTransactionDto toDto(InventoryTransaction entity);
}
