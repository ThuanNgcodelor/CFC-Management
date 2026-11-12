package db.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.io.IOException;
import java.util.*;

/** Additive access tables and bounded backfill from immutable snapshots, never current HR/campaign data. */
public class V37__add_health_personal_access extends BaseJavaMigration {
    private record FrozenYear(String id,int year) { }
    @Override public void migrate(Context context) throws Exception {
        var connection=context.getConnection();
        try(var resource=getClass().getResourceAsStream("/health/migrations/personal_access.sql")) {
            if(resource==null)throw new IOException("Missing health personal access schema resource");
            String sql=new String(resource.readAllBytes(),StandardCharsets.UTF_8).replaceAll("(?m)^--.*$","");
            try(var statement=connection.createStatement()){for(String command:sql.split(";"))if(!command.isBlank())statement.execute(command);}
        }
        String after="";var json=new ObjectMapper();
        while(true) {
            var batch=new ArrayList<FrozenYear>();
            try(var query=connection.prepareStatement("SELECT id,snapshot FROM hr_health_publications WHERE id>? AND content_hash IS NOT NULL ORDER BY id")) {
                query.setString(1,after);query.setMaxRows(20);
                try(var rows=query.executeQuery()) {
                    while(rows.next()) {
                        String raw=rows.getString(2);if(raw.length()>2*1024*1024)throw new SQLException("Health publication snapshot exceeds supported size");
                        var root=json.readTree(raw);var year=root.path("visit").path("year");
                        if(root.path("format").asInt()!=1||!year.isInt()||year.asInt()<1900||year.asInt()>2200)throw new SQLException("Health publication needs valid frozen year before personal access migration");
                        batch.add(new FrozenYear(rows.getString(1),year.asInt()));
                    }
                }
            }
            if(batch.isEmpty())break;
            try(var update=connection.prepareStatement("UPDATE hr_health_publications SET report_year=? WHERE id=?")) {
                for(var row:batch){update.setInt(1,row.year());update.setString(2,row.id());update.addBatch();}update.executeBatch();
            }
            after=batch.getLast().id();
        }
    }
}
