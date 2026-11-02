package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

/** Expands the legacy MySQL enum before Hibernate validates or updates users. */
public class V29__add_hr_staff_role extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        if (!"MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
            return;
        }
        // A fresh installation has no legacy users table until Hibernate runs.
        // VARCHAR columns already accept the new value and need no conversion.
        try (var query = connection.prepareStatement("""
                SELECT COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'role'
                """); var columns = query.executeQuery()) {
            if (!columns.next()) return;
            String type = columns.getString("COLUMN_TYPE");
            if (!type.toLowerCase(Locale.ROOT).startsWith("enum(") || type.contains("'HR_STAFF'")) return;
            String expanded = type.substring(0, type.length() - 1) + ",'HR_STAFF')";
            String nullable = "YES".equals(columns.getString("IS_NULLABLE")) ? " NULL" : " NOT NULL";
            String defaultValue = columns.getString("COLUMN_DEFAULT");
            String defaultClause = defaultValue == null ? "" : " DEFAULT '" + defaultValue.replace("'", "''") + "'";
            try (var statement = connection.createStatement()) {
                statement.execute("ALTER TABLE users MODIFY COLUMN role " + expanded + nullable + defaultClause);
            }
        }
    }
}
