package com.booking.system.health;

import db.migration.V37__add_health_personal_access;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import java.sql.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthPersonalMigrationTest {
    @Test void backfillUsesFrozenSnapshotYearAndDoesNotAlterClinicalPayload() throws Exception {
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:health_access_backfill;MODE=MySQL;DATABASE_TO_LOWER=TRUE","sa","")) {
            try(var statement=connection.createStatement()) {
                statement.execute("CREATE TABLE hr_employees(id VARCHAR(36) PRIMARY KEY)");
                statement.execute("CREATE TABLE hr_health_publications(id VARCHAR(36) PRIMARY KEY,snapshot LONGTEXT,content_hash VARCHAR(64))");
            }
            String payload="{\"format\":1,\"visit\":{\"year\":2024},\"results\":[{\"raw\":\"<34\"}]}";
            try(var insert=connection.prepareStatement("INSERT INTO hr_health_publications(id,snapshot,content_hash) VALUES(?,?,?)")) {
                for(int i=0;i<43;i++){insert.setString(1,"release-"+String.format("%03d",i));insert.setString(2,payload);insert.setString(3,"a".repeat(64));insert.addBatch();}insert.executeBatch();
            }
            var context=mock(Context.class);when(context.getConnection()).thenReturn(connection);new V37__add_health_personal_access().migrate(context);
            try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT count(*),min(report_year),max(report_year) FROM hr_health_publications")) {
                assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isEqualTo(43);assertThat(rows.getInt(2)).isEqualTo(2024);assertThat(rows.getInt(3)).isEqualTo(2024);
            }
            try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT snapshot FROM hr_health_publications")) {while(rows.next())assertThat(rows.getString(1)).isEqualTo(payload);}
        }
    }
}
