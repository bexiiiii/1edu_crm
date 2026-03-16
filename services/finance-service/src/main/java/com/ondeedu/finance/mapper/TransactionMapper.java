package com.ondeedu.finance.mapper;

import com.ondeedu.finance.dto.CreateTransactionRequest;
import com.ondeedu.finance.dto.TransactionDto;
import com.ondeedu.finance.dto.UpdateTransactionRequest;
import com.ondeedu.finance.entity.Transaction;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TransactionMapper {

    TransactionDto toDto(Transaction transaction);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "staffId", ignore = true)
    @Mapping(target = "salaryMonth", ignore = true)
    Transaction toEntity(CreateTransactionRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "staffId", ignore = true)
    @Mapping(target = "salaryMonth", ignore = true)
    void updateEntity(@MappingTarget Transaction transaction, UpdateTransactionRequest request);
}
