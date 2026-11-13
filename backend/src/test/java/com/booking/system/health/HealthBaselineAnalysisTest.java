package com.booking.system.health;

import com.booking.system.health.importer.*;
import com.booking.system.health.importer.baseline.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.booking.system.health.importer.baseline.HealthBaselineData.*;

class HealthBaselineAnalysisTest {
    final HealthBaselineReader reader=new HealthBaselineReader(new HealthWorkbookParser());
    final HealthBaselineAnalyzer analyzer=new HealthBaselineAnalyzer();
    @TempDir Path temporary;
    static WorkbookInput fixture(String sheet,String sid,String name,String code,String value,String classification) throws Exception {
        try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var page=book.createSheet(sheet);String[] headers={"SID","Họ tên","Mã nhân viên","Năm sinh","AST (máy A)","AST (máy A) - Giới hạn b/t","Phân loại"};
            String[] values={sid,name,code,"1990",value,"<34",classification};
            for(int c=0;c<headers.length;c++) {if(c==0){page.createRow(0);page.createRow(1);}page.getRow(0).createCell(c).setCellValue(headers[c]);page.getRow(1).createCell(c).setCellValue(values[c]);}
            book.write(out);return new WorkbookInput(Kind.HISTORY_2023_2024,"fixture.xlsx",out.toByteArray());
        }
    }
    @Test void devToolRejectsEveryProductionDatabaseUrl() {
        assertThatCode(() -> HealthBaselineDev.requireLocalDatabase("jdbc:h2:file:/tmp/health-review/db")).doesNotThrowAnyException();
        for(String url:List.of("jdbc:mysql://localhost/db","jdbc:mysql://server/production","jdbc:h2:tcp://server/db","jdbc:h2:file:/home/user/db"))
            assertThatThrownBy(() -> HealthBaselineDev.requireLocalDatabase(url)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void normalizationKeepsPrecisionAndDoesNotInventClassification() {
        assertThat(HealthClassification.normalize(" Ii. ")).isEqualTo("II");
        assertThat(HealthClassification.normalize("IV")).isEqualTo("IV");assertThat(HealthClassification.normalize("Bình thường")).isNull();
        assertThat(HealthClassification.normalize("")).isNull();assertThat(HealthClassification.normalize("I.I")).isNull();
    }
    @Test void codeMatchIsAutomaticButNameOnlyStaysPending() throws Exception {
        var person=new Identity("fixture-person","DEV01","Người giả lập","1990-01-01");
        var data=reader.read(List.of(fixture("Tổng hợp","010123-DEV","Người giả lập","DEV01","<0.5","II.")));
        var report=analyzer.analyze(data,List.of(person));assertThat(report.matchedCodeRows()).isEqualTo(1);
        assertThat(data.sources().getFirst().rows().getFirst().values().getFirst().rawValue()).isEqualTo("<0.5");
        var missing=reader.read(List.of(fixture("Tổng hợp","010123-DEV","Người giả lập","","<0.5","II.")));
        var pending=analyzer.analyze(missing,List.of(person));assertThat(pending.matchedCodeRows()).isZero();assertThat(pending.suggestedOnlyRows()).isEqualTo(1);
        assertThat(pending.findings()).anyMatch(f -> f.kind().equals("PERSON_CONFIRMATION"));
    }
    @Test void valuesAndSourceNamesAreComparedWithoutCreatingDuplicateVisits() throws Exception {
        var one=reader.read(List.of(fixture("Tổng hợp","010123-DEV","Người giả lập","DEV01","20","II")));
        var two=reader.read(List.of(fixture("Sheet5","010123-DEV","Tên nguồn khác","","21","Lời hướng dẫn")));
        var sources=new ArrayList<>(one.sources());sources.addAll(two.sources());var data=new Dataset(sources,one.indicatorLabels());
        var report=analyzer.analyze(data,List.of());assertThat(report.sourceRows()).isEqualTo(2);assertThat(report.distinctSids()).isEqualTo(1);
        assertThat(report.sourceNameConflicts()).isEqualTo(1);assertThat(report.sourceValueConflicts()).isEqualTo(2);
        assertThat(report.findingCounts().get("CLASSIFICATION_INVALID")).isEqualTo(1);
        HealthBaselineReportWriter.write(temporary,data,report,List.of());
        assertThat(Files.getPosixFilePermissions(temporary.resolve("baseline-private.json"))).isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        assertThat(Files.readString(temporary.resolve("baseline-review-private.csv"))).contains("VALUE_CONFLICT");
    }
    @Test @EnabledIfEnvironmentVariable(named="HEALTH_SOURCE_DIRECTORY",matches=".+")
    void readsAllThreeRealSourcesWithoutPersistingOrPrintingPeople() throws Exception {
        var root=Path.of(System.getenv("HEALTH_SOURCE_DIRECTORY"));
        var inputs=List.of(new WorkbookInput(Kind.HISTORY_2023_2024,"history.xlsx",Files.readAllBytes(root.resolve("So sánh kết quả sức khỏe gián tiếp 2023 - 2024.xlsx"))),
            new WorkbookInput(Kind.SUPPLEMENT_2024,"supplement.xlsx",Files.readAllBytes(root.resolve("bo sung thong tin SK 2024.xlsx"))),
            new WorkbookInput(Kind.RESULTS_2025,"2025.xlsx",Files.readAllBytes(root.resolve("KHAM SUC KHOE 2025.xlsx"))));
        var data=reader.read(inputs);var report=analyzer.analyze(data,List.of());
        assertThat(report.sourceRows()).isEqualTo(1019);assertThat(report.distinctSids()).isEqualTo(549);assertThat(report.taskRows()).isEqualTo(23);
        assertThat(report.findings().stream().filter(f -> f.sourceKey().endsWith(":Sheet5") && (f.kind().equals("NAME_VARIANT")||f.kind().equals("NAME_CONFLICT"))).count()).isEqualTo(32);
        System.out.println("HEALTH_BASELINE_READ_ONLY_OK sourceRows="+report.sourceRows()+" uniqueSids="+report.distinctSids()+" tasks="+report.taskRows());
    }
}
