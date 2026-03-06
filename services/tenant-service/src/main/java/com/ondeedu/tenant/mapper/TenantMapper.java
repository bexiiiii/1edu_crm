package com.ondeedu.tenant.mapper;

import com.ondeedu.tenant.dto.CreateTenantRequest;
import com.ondeedu.tenant.dto.TenantDto;
import com.ondeedu.tenant.dto.UpdateTenantRequest;
import com.ondeedu.tenant.entity.Tenant;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TenantMapper {

    TenantDto toDto(Tenant tenant);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "schemaName", ignore = true)
    Tenant toEntity(CreateTenantRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "schemaName", ignore = true)
    @Mapping(target = "subdomain", ignore = true)
    void updateEntity(@MappingTarget Tenant tenant, UpdateTenantRequest request);
}
