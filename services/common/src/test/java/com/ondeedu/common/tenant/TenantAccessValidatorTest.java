package com.ondeedu.common.tenant;

import com.ondeedu.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantAccessValidatorTest {

    @Test
    void shouldUseHeaderTenantWhenItMatchesJwt() {
        String tenantId = TenantAccessValidator.resolveTenantId("tenant-a", "tenant-a", false);

        assertThat(tenantId).isEqualTo("tenant-a");
    }

    @Test
    void shouldRejectMismatchForRegularUsers() {
        assertThatThrownBy(() -> TenantAccessValidator.resolveTenantId("tenant-a", "tenant-b", false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Authenticated tenant does not match the requested tenant context");
    }

    @Test
    void shouldAllowSuperAdminHeaderOverride() {
        String tenantId = TenantAccessValidator.resolveTenantId("tenant-a", "tenant-b", true);

        assertThat(tenantId).isEqualTo("tenant-a");
    }

    @Test
    void shouldFallbackToJwtTenantWhenHeaderMissing() {
        String tenantId = TenantAccessValidator.resolveTenantId(null, "tenant-a", false);

        assertThat(tenantId).isEqualTo("tenant-a");
    }

    @Test
    void shouldReportMismatchOnlyForRegularUsers() {
        assertThat(TenantAccessValidator.hasMismatch("tenant-a", "tenant-b", false)).isTrue();
        assertThat(TenantAccessValidator.hasMismatch("tenant-a", "tenant-b", true)).isFalse();
    }
}
