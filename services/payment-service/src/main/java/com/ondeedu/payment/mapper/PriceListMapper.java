package com.ondeedu.payment.mapper;

import com.ondeedu.payment.dto.CreatePriceListRequest;
import com.ondeedu.payment.dto.PriceListDto;
import com.ondeedu.payment.dto.UpdatePriceListRequest;
import com.ondeedu.payment.entity.PriceList;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PriceListMapper {

    PriceListDto toDto(PriceList priceList);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    PriceList toEntity(CreatePriceListRequest request);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(@MappingTarget PriceList priceList, UpdatePriceListRequest request);
}
