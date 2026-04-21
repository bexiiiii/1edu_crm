package com.ondeedu.common.tenant;

import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Applies tenant branch context to every JDBC connection so RLS policies
 * can consistently enforce branch isolation for both JPA and JdbcTemplate flows.
 */
public class BranchAwareDataSource extends AbstractDataSource {

    private static final String APPLY_BRANCH_SQL = "SELECT set_config('app.branch_id', ?, false)";

    private final DataSource delegate;

    public BranchAwareDataSource(DataSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return applyBranch(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return applyBranch(delegate.getConnection(username, password));
    }

    private Connection applyBranch(Connection connection) throws SQLException {
        String branchId = TenantContext.getBranchId();
        String value = StringUtils.hasText(branchId) ? branchId.trim() : "";

        try (PreparedStatement statement = connection.prepareStatement(APPLY_BRANCH_SQL)) {
            statement.setString(1, value);
            statement.execute();
        }

        return connection;
    }
}
