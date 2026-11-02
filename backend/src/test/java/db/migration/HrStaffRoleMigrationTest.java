package db.migration;

import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class HrStaffRoleMigrationTest {
    @Test
    void expandsLegacyEnumWithoutDroppingExistingRolesNullabilityOrDefault() throws Exception {
        Context context = mock(Context.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        PreparedStatement query = mock(PreparedStatement.class);
        ResultSet columns = mock(ResultSet.class);
        Statement statement = mock(Statement.class);
        when(context.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(connection.prepareStatement(anyString())).thenReturn(query);
        when(query.executeQuery()).thenReturn(columns);
        when(columns.next()).thenReturn(true);
        when(columns.getString("COLUMN_TYPE")).thenReturn("enum('ADMIN','EMPLOYEE','MANAGER')");
        when(columns.getString("IS_NULLABLE")).thenReturn("NO");
        when(columns.getString("COLUMN_DEFAULT")).thenReturn("EMPLOYEE");
        when(connection.createStatement()).thenReturn(statement);

        new V29__add_hr_staff_role().migrate(context);

        verify(statement).execute("ALTER TABLE users MODIFY COLUMN role enum('ADMIN','EMPLOYEE','MANAGER','HR_STAFF') NOT NULL DEFAULT 'EMPLOYEE'");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"enum('ADMIN','MANAGER','EMPLOYEE','HR_STAFF')", "varchar(255)"})
    void leavesCompatibleColumnsUntouched(String type) throws Exception {
        Context context = mock(Context.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        PreparedStatement query = mock(PreparedStatement.class);
        ResultSet columns = mock(ResultSet.class);
        when(context.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(connection.prepareStatement(anyString())).thenReturn(query);
        when(query.executeQuery()).thenReturn(columns);
        when(columns.next()).thenReturn(true);
        when(columns.getString("COLUMN_TYPE")).thenReturn(type);
        new V29__add_hr_staff_role().migrate(context);
        verify(connection, never()).createStatement();
    }
}
