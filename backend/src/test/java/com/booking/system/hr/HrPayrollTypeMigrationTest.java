package com.booking.system.hr;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class HrPayrollTypeMigrationTest {
    @Test void upgrading39PreservesOldImportsAndDefaultsThemToStandard() throws Exception {
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:payroll_type_upgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");var statement=connection.createStatement()) {
            var source=new SingleConnectionDataSource(connection,true);
            Flyway.configure().dataSource(source).locations("classpath:db/migration").target("39").load().migrate();
            statement.execute("INSERT INTO hr_payroll_imports(id,source_file_name,file_sha256,file_size,source_sheet_name,payroll_month,status,created_by_actor,updated_by_actor) VALUES('old-payroll','fixture.xlsx','"+"a".repeat(64)+"',1,'Sheet','10/2026','PREVIEWED','QA','QA')");
            var latest=Flyway.configure().dataSource(source).locations("classpath:db/migration").load();assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
            try(var result=statement.executeQuery("SELECT payroll_type,source_file_name FROM hr_payroll_imports WHERE id='old-payroll'")){assertThat(result.next()).isTrue();assertThat(result.getString(1)).isEqualTo("STANDARD");assertThat(result.getString(2)).isEqualTo("fixture.xlsx");}
            statement.execute("UPDATE hr_payroll_imports SET payroll_type='LEADERSHIP' WHERE id='old-payroll'");
            assertThat(latest.migrate().migrationsExecuted).isZero();
        }
    }
}
