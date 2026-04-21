package com.ondeedu.common.tenant;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class TenantSchemaConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;

    public TenantSchemaConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setSchema(TenantSchemaResolver.defaultSchema());
        applyBranchContext(connection);
        return connection;
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = getAnyConnection();
        String schema = StringUtils.hasText(tenantIdentifier)
            ? tenantIdentifier
            : TenantSchemaResolver.defaultSchema();
        connection.setSchema(schema);
        applyBranchContext(connection);
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        connection.setSchema(TenantSchemaResolver.defaultSchema());
        clearBranchContext(connection);
        releaseAnyConnection(connection);
    }

    private void applyBranchContext(Connection connection) throws SQLException {
        String branchId = TenantContext.getBranchId();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('app.branch_id', ?, false)")) {
            statement.setString(1, StringUtils.hasText(branchId) ? branchId.trim() : "");
            statement.execute();
        }
    }

    private void clearBranchContext(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('app.branch_id', ?, false)")) {
            statement.setString(1, "");
            statement.execute();
        }
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
