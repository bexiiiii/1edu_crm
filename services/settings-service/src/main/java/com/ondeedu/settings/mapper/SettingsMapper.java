package com.ondeedu.settings.mapper;

import com.ondeedu.settings.dto.SettingsDto;
import com.ondeedu.settings.dto.UpdateSettingsRequest;
import com.ondeedu.settings.entity.TenantSettings;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface SettingsMapper {

    SettingsDto toDto(TenantSettings settings);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "kpayEnabled", ignore = true)
    @Mapping(target = "kpayMerchantId", ignore = true)
    @Mapping(target = "kpayApiBaseUrl", ignore = true)
    @Mapping(target = "kpayRecipientField", ignore = true)
    @Mapping(target = "kpayApiKey", ignore = true)
    @Mapping(target = "kpayApiSecret", ignore = true)
    @Mapping(target = "apipayEnabled", ignore = true)
    @Mapping(target = "apipayApiBaseUrl", ignore = true)
    @Mapping(target = "apipayRecipientField", ignore = true)
    @Mapping(target = "apipayApiKey", ignore = true)
    @Mapping(target = "apipayWebhookSecret", ignore = true)
    @Mapping(target = "aisarEnabled", ignore = true)
    @Mapping(target = "aisarApiBaseUrl", ignore = true)
    @Mapping(target = "aisarApiKey", ignore = true)
    @Mapping(target = "aisarWebhookSecret", ignore = true)
    @Mapping(target = "ftelecomEnabled", ignore = true)
    @Mapping(target = "ftelecomApiBaseUrl", ignore = true)
    @Mapping(target = "ftelecomCrmToken", ignore = true)
    void updateEntity(@MappingTarget TenantSettings settings, UpdateSettingsRequest request);
}
