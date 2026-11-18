package com.booking.system.health;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Opt-in, destructive fixtures; refuses every DB except the dedicated loopback QA schema. */
@EnabledIfSystemProperty(named="health.mysql.qa.url", matches="jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/health_qa_acceptance(?:\\?.*)?")
@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
class HealthMysqlAcceptanceTest extends HealthPersonalAccessTest {
    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry properties) {
        String url=System.getProperty("health.mysql.qa.url");
        if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/health_qa_acceptance(?:\\?.*)?"))throw new IllegalStateException("MySQL QA requires isolated loopback schema");
        String password=System.getenv("HEALTH_MYSQL_QA_PASSWORD");
        if(password==null||password.isBlank())throw new IllegalStateException("Missing private QA password");
        properties.add("spring.datasource.url",()->url);
        properties.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
        properties.add("spring.datasource.username",()->"health_qa");
        properties.add("spring.datasource.password",()->password);
        properties.add("spring.jpa.database-platform",()->"org.hibernate.dialect.MySQLDialect");
        properties.add("spring.jpa.properties.hibernate.dialect",()->"org.hibernate.dialect.MySQLDialect");
        properties.add("logging.level.org.springframework.jdbc",()->"INFO");
        properties.add("logging.level.org.springframework.boot.security.autoconfigure",()->"ERROR");
    }
    @org.junit.jupiter.api.Test
    void leavesSyntheticReleaseForBackupRestoreAndShowsMysqlLatestPlan() {
        released();issue();
        var plan=jdbc.queryForList("EXPLAIN SELECT p.id FROM hr_health_publications p WHERE p.status='PUBLISHED' AND NOT EXISTS(SELECT x.id FROM hr_health_publications x WHERE x.visit_id=p.visit_id AND x.revision>p.revision)");
        org.assertj.core.api.Assertions.assertThat(plan).isNotEmpty();
        System.out.println("HEALTH_MYSQL_LATEST_EXPLAIN="+plan);
    }

    @org.junit.jupiter.api.Test
    void upgradesExistingV33AndBackfillsFrozenV36ReleaseOnMysql() throws Exception {
        String url=System.getProperty("health.mysql.qa.url").replace("/health_qa_acceptance","/health_qa_upgrade");
        String password=System.getenv("HEALTH_MYSQL_QA_PASSWORD");
        org.flywaydb.core.Flyway.configure().dataSource(url,"health_qa",password).target("33").load().migrate();
        long before;
        try(var connection=java.sql.DriverManager.getConnection(url,"health_qa",password);var statement=connection.createStatement();var rows=statement.executeQuery("SELECT COUNT(*) FROM hr_employees")){rows.next();before=rows.getLong(1);}
        org.flywaydb.core.Flyway.configure().dataSource(url,"health_qa",password).target("36").load().migrate();
        String payload="{\"format\":1,\"visit\":{\"year\":2024},\"results\":[{\"raw\":\"<34\"}]}";
        try(var connection=java.sql.DriverManager.getConnection(url,"health_qa",password);var statement=connection.createStatement()) {
            statement.executeUpdate("INSERT INTO hr_health_campaigns(id,code,name,report_year,provider,workforce_group,status,created_by_actor,updated_by_actor) VALUES('qa-campaign','QA-UPGRADE','Đợt giả lập',2024,'Cơ sở giả lập','BOTH','OPEN','QA','QA')");
            statement.executeUpdate("INSERT INTO hr_employees(id,employee_code,full_name,gender,employment_status,workforce_group,onboarding_source,onboarding_policy_version,created_by_actor,updated_by_actor) VALUES('qa-employee','QA-UPGRADE','Nhân viên giả lập','UNKNOWN','DRAFT','OFFICE','LEGACY',1,'QA','QA')");
            statement.executeUpdate("INSERT INTO hr_health_subjects(id,employee_id,created_by_actor,updated_by_actor) VALUES('qa-subject','qa-employee','QA','QA')");
            statement.executeUpdate("INSERT INTO hr_health_visits(id,subject_id,campaign_id,source_namespace,status,created_by_actor,updated_by_actor) VALUES('qa-visit','qa-subject','qa-campaign','QA','CONFIRMED','QA','QA')");
            try(var insert=connection.prepareStatement("INSERT INTO hr_health_publications(id,visit_id,revision,status,snapshot,content_hash,created_by_actor,updated_by_actor) VALUES('qa-release','qa-visit',1,'PUBLISHED',?,?,'QA','QA')")){insert.setString(1,payload);insert.setString(2,"a".repeat(64));insert.executeUpdate();}
        }
        var flyway=org.flywaydb.core.Flyway.configure().dataSource(url,"health_qa",password).load();
        org.assertj.core.api.Assertions.assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);flyway.validate();
        org.assertj.core.api.Assertions.assertThat(flyway.migrate().migrationsExecuted).isZero();
        try(var connection=java.sql.DriverManager.getConnection(url,"health_qa",password);var statement=connection.createStatement()) {
            try(var rows=statement.executeQuery("SELECT COUNT(*) FROM hr_employees")){rows.next();org.assertj.core.api.Assertions.assertThat(rows.getLong(1)).isEqualTo(before+1);}
            try(var rows=statement.executeQuery("SELECT snapshot,report_year FROM hr_health_publications WHERE id='qa-release'")){rows.next();org.assertj.core.api.Assertions.assertThat(rows.getString(1)).isEqualTo(payload);org.assertj.core.api.Assertions.assertThat(rows.getInt(2)).isEqualTo(2024);}
        }
        System.out.println("HEALTH_MYSQL_UPGRADE_V33_TO_V39_OK existingEmployeesPreserved=true snapshotUnchanged=true");
    }

    @org.junit.jupiter.api.AfterAll
    void syntheticBackupFixture(){fixtures();fakeIdentity();released();issue();}

}
