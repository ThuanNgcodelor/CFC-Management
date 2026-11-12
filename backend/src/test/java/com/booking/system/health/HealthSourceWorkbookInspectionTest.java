package com.booking.system.health;

import com.booking.system.health.importer.HealthWorkbookParser;
import com.booking.system.health.importer.HealthImportSupport;
import com.booking.system.health.api.HrHealthImportDtos.*;
import java.util.HashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;

// Opt-in, read-only verification: source files are never written or imported into a database.
@EnabledIfEnvironmentVariable(named="HEALTH_SOURCE_DIRECTORY",matches=".+")
class HealthSourceWorkbookInspectionTest {
    @Test void inspectsExistingWorkbookLayoutsWithoutPersistingOrLoggingPeople() throws Exception {
        var root=Path.of(System.getenv("HEALTH_SOURCE_DIRECTORY"));var parser=new HealthWorkbookParser();
        var current=parser.inspect(Files.readAllBytes(root.resolve("KHAM SUC KHOE 2025.xlsx")));
        assertThat(current.sheets()).hasSize(1);assertThat(current.sheets().getFirst().dataRows()).isEqualTo(85);
        assertThat(current.sheets().getFirst().columns()).hasSize(47);
        var historical=parser.inspect(Files.readAllBytes(root.resolve("So sánh kết quả sức khỏe gián tiếp 2023 - 2024.xlsx")));
        assertThat(historical.sheets()).hasSize(7);assertThat(historical.sheets().stream().filter(s -> s.hidden()).count()).isEqualTo(6);
        assertThat(historical.sheets().stream().filter(s -> s.name().equals("Tổng hợp")).findFirst().orElseThrow().dataRows()).isEqualTo(464);
        var extra=parser.inspect(Files.readAllBytes(root.resolve("bo sung thong tin SK 2024.xlsx")));
        assertThat(extra.sheets()).hasSize(2);
        var results=extra.sheets().stream().filter(s -> s.columns().size()==136).findFirst().orElseThrow();
        assertThat(results.dataRows()).isEqualTo(13);
        verifyParsing(parser,Files.readAllBytes(root.resolve("KHAM SUC KHOE 2025.xlsx")),current.sheets().getFirst(),85);
        verifyParsing(parser,Files.readAllBytes(root.resolve("So sánh kết quả sức khỏe gián tiếp 2023 - 2024.xlsx")),historical.sheets().stream().filter(s -> s.name().equals("Tổng hợp")).findFirst().orElseThrow(),464);
        verifyParsing(parser,Files.readAllBytes(root.resolve("bo sung thong tin SK 2024.xlsx")),results,13);
        System.out.println("HEALTH_READ_ONLY_SOURCE_LAYOUTS_OK workbooks=3 sheets=10 parsedResultRows=85/464/13");
    }
    private static void verifyParsing(HealthWorkbookParser parser, byte[] bytes, SheetInfo sheet, int expected) {
        var columns=new HashMap<Integer,Mapping>();
        var result=sheet.columns().stream().filter(c -> HealthImportSupport.normalized(c.name()).contains("ast")).findFirst().orElseThrow();
        for (var column : sheet.columns()) columns.put(column.index(),new Mapping(column.index()==result.index() ? Field.RESULT : column.suggested(),column.index()==result.index() ? "fixture-indicator" : null,null,null));
        var request=new StageRequest("fixture-campaign",sheet.name(),sheet.headerRow(),1,"READ_ONLY_DEV",false,null,columns,null);
        var parsed=parser.parse(bytes,request);assertThat(parsed.rows().size()).isEqualTo(expected);
        assertThat(parsed.rows().stream().filter(r -> !r.values().isEmpty()).count()).isPositive();
    }
}
