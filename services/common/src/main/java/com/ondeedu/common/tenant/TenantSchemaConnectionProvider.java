package com.ondeedu.common.tenant;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Slf4j
public class TenantSchemaConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;

    public TenantSchemaConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
        log.debug("TenantSchemaConnectionProvider initialized with {}", dataSource.getClass().getName());
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setSchema(TenantSchemaResolver.defaultSchema());
        log.debug("Obtained generic connection with schema {}", TenantSchemaResolver.defaultSchema());
        return connection;
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = getAnyConnection();
        String schema = tenantIdentifier != null ? tenantIdentifier : TenantSchemaResolver.defaultSchema();
        connection.setSchema(schema);
        log.debug("Obtained tenant connection for schema {}", schema);
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        connection.setSchema(TenantSchemaResolver.defaultSchema());
        log.debug("Released tenant connection for schema {}", tenantIdentifier);
        releaseAnyConnection(connection);
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        throw new UnsupportedOperationException("Cannot unwrap to " + unwrapType);
    }
}
