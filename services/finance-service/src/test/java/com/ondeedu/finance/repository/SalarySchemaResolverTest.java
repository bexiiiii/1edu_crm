package com.ondeedu.finance.repository;

import com.ondeedu.common.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalarySchemaResolverTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldResolveSchemaFromSystemTenantsBeforeUsingThreadLocalSchema() {
        SalarySchemaResolver resolver = new SalarySchemaResolver(entityManager);
        String tenantId = "c1ae4737-bf1d-41e5-807b-5b1f6c85ec22";

        TenantContext.setTenantId(tenantId);
        TenantContext.setSchemaName("tenant_" + tenantId.replace("-", ""));

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter("tenantId", UUID.fromString(tenantId))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of("tenant_default"));

        assertThat(resolver.resolveCurrentSchema()).isEqualTo("tenant_default");
        verify(query).setParameter("tenantId", UUID.fromString(tenantId));
    }

    @Test
    void shouldFallbackToThreadLocalSchemaWhenTenantLookupMisses() {
        SalarySchemaResolver resolver = new SalarySchemaResolver(entityManager);

        TenantContext.setTenantId("0d8da98f-6b8e-4196-a193-6a6c8dcea090");
        TenantContext.setSchemaName("tenant_0d8da98f6b8e4196a1936a6c8dcea090");

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter("tenantId", UUID.fromString("0d8da98f-6b8e-4196-a193-6a6c8dcea090"))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        assertThat(resolver.resolveCurrentSchema())
                .isEqualTo("tenant_0d8da98f6b8e4196a1936a6c8dcea090");
    }
}
