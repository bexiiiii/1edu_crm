package com.ondeedu.common.config;

import com.ondeedu.common.tenant.TenantIdentifierResolver;
import com.ondeedu.common.tenant.TenantSchemaConnectionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TenantJpaConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TenantJpaConfig.class)
            .withBean(DataSource.class, () -> mock(DataSource.class));

    @Test
    void registersTenantAwareHibernateBeansWhenDataSourceExists() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TenantSchemaConnectionProvider.class);
            assertThat(context).hasSingleBean(TenantIdentifierResolver.class);
            assertThat(context).hasSingleBean(HibernatePropertiesCustomizer.class);
        });
    }
}
