package com.ondeedu.payment.mapper;

import com.ondeedu.payment.dto.CreateSubscriptionRequest;
import com.ondeedu.payment.dto.SubscriptionDto;
import com.ondeedu.payment.dto.UpdateSubscriptionRequest;
import com.ondeedu.payment.entity.Subscription;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface SubscriptionMapper {

    SubscriptionDto toDto(Subscription subscription);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "lessonsLeft", source = "totalLessons")
    Subscription toEntity(CreateSubscriptionRequest request);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "studentId", ignore = true)
    @Mapping(target = "courseId", ignore = true)
    @Mapping(target = "groupId", ignore = true)
    @Mapping(target = "serviceId", ignore = true)
    @Mapping(target = "priceListId", ignore = true)
    @Mapping(target = "totalLessons", ignore = true)
    @Mapping(target = "startDate", ignore = true)
    @Mapping(target = "amount", ignore = true)
    @Mapping(target = "currency", ignore = true)
    void updateEntity(@MappingTarget Subscription subscription, UpdateSubscriptionRequest request);
}
