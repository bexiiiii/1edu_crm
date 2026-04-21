package com.ondeedu.common.config;

import com.ondeedu.common.tenant.BranchAwareDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
@ConditionalOnBean(DataSource.class)
public class BranchDataSourceConfig {

    @Bean
    @Primary
    public DataSource branchAwareDataSource(@Qualifier("dataSource") DataSource dataSource) {
        return new BranchAwareDataSource(dataSource);
    }
}
