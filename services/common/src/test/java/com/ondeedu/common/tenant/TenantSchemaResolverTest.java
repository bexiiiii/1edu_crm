package com.ondeedu.common.tenant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantSchemaResolverTest {

    @Test
    void shouldNormalizeUuidTenantIdsToSchemaNames() {
        String schema = TenantSchemaResolver.schemaNameForTenantId("0fed0e47-2d6c-4062-ac8f-3c82496d8bfc");

        assertThat(schema).isEqualTo("tenant_0fed0e472d6c4062ac8f3c82496d8bfc");
    }

    @Test
    void shouldRejectUnsafeTenantIds() {
        String schema = TenantSchemaResolver.schemaNameForTenantId("tenant;drop schema system");

        assertThat(schema).isNull();
    }
}
