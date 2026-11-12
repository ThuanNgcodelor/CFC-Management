package com.booking.system.health;

import com.booking.system.health.api.HrHealthImportDtos.*;
import com.booking.system.health.entity.*;
import com.booking.system.health.importer.*;
import com.booking.system.health.importer.baseline.*;
import com.booking.system.health.repository.*;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.importer.HrImportActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.booking.system.health.importer.baseline.HealthBaselineData.*;

@SpringBootTest(classes=HealthPhase2Test.Config.class,properties={
 "spring.datasource.url=jdbc:h2:mem:health_baseline;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false","spring.flyway.enabled=true","cfc.health.enabled=true",
 "logging.level.org.springframework.web=INFO","logging.level.org.hibernate.SQL=OFF"})
class HealthBaselineIntegrationTest {
    static final HrImportActor ADMIN=new HrImportActor("USER:baseline-fixture","Baseline fixture","ADMIN");
    @jakarta.annotation.Resource HealthBaselineReader reader;
    @jakarta.annotation.Resource HealthBaselineAnalyzer analyzer;
    @jakarta.annotation.Resource HealthBaselinePreparation preparation;
    @jakarta.annotation.Resource HealthImportService imports;
    @jakarta.annotation.Resource HealthImportWriter writer;
    @jakarta.annotation.Resource HealthSourceCorrectionService corrections;
    @jakarta.annotation.Resource HealthBaselineYearConfirmation years;
    @jakarta.annotation.Resource HealthImportRowRepository rows;
    @jakarta.annotation.Resource HealthResultRepository results;
    @jakarta.annotation.Resource HealthEmployeeRepository people;
    @jakarta.annotation.Resource JdbcTemplate jdbc;

    @BeforeEach void clearAndSeed() {
        for(String table:List.of("result_changes","results","publications","import_rows","imports","visits","subjects","mapping_profiles","indicator_aliases","indicators","campaigns","audit_events")) jdbc.update("delete from hr_health_"+table);
        jdbc.update("delete from hr_employees where employee_code like 'BASEDEV%'");
        var employee=new HrEmployee();employee.setEmployeeCode("BASEDEV01");employee.setFullName("Nhân viên baseline giả lập");employee.setCreatedByActor(ADMIN.subject());employee.setUpdatedByActor(ADMIN.subject());people.saveAndFlush(employee);
    }
    Dataset dataset(String classification) throws Exception {
        return reader.read(List.of(HealthBaselineAnalysisTest.fixture("Tổng hợp","010123-BASE","Nhân viên baseline giả lập","BASEDEV01","21",classification)));
    }
    HealthBaselinePreparation.Prepared prepare(Dataset data,boolean confirmed) {
        return preparation.prepare(data,analyzer.analyze(data,preparation.identities()),"CFC_HISTORY",Policy.TOTAL_FIRST,confirmed,ADMIN);
    }
    @Test void preparesOnlyHistoricalCampaignsAndCanRetryWithoutDuplicatingBatches() throws Exception {
        var data=dataset("II.");var report=prepare(data,true);assertThat(report.batches()).hasSize(1);assertThat(results.count()).isZero();
        var row=imports.preview(report.batches().getFirst().id(),0,20,ADMIN).content().getFirst();assertThat(row.errors()).isEmpty();
        assertThat(row.warnings()).anyMatch(message -> message.contains("hiển thị chuẩn: II"));
        var again=prepare(data,true);assertThat(again.batches().getFirst().id()).isEqualTo(report.batches().getFirst().id());
        assertThat(jdbc.queryForObject("select count(*) from hr_health_imports",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from hr_health_campaigns where report_year=2026",Integer.class)).isZero();
        writer.confirm(report.batches().getFirst().id(),new ConfirmRequest(List.of(new RowDecision(row.id(),row.previewToken(),Map.of(),null))),ADMIN);
        assertThat(results.count()).isEqualTo(2);assertThat(jdbc.queryForObject("select count(*) from hr_health_publications",Integer.class)).isZero();
    }
    @Test void unverifiedYearsAndMissingPeopleAreNotSilentlyConfirmed() throws Exception {
        var prepared=prepare(dataset("II"),false);var batch=prepared.batches().getFirst();var row=imports.preview(batch.id(),0,20,ADMIN).content().getFirst();
        assertThat(row.errors()).anyMatch(message -> message.contains("chưa được xác minh"));
        assertThatThrownBy(() -> writer.confirm(batch.id(),new ConfirmRequest(List.of(new RowDecision(row.id(),row.previewToken(),Map.of(),null))),ADMIN)).hasMessageContaining("còn lỗi");
        assertThat(results.count()).isZero();
    }
    @Test void classificationCorrectionPreservesOriginalAndRequiresCurrentVersionAndReason() throws Exception {
        var prepared=prepare(dataset("Lời hướng dẫn sai ô"),true);var batch=prepared.batches().getFirst();var row=imports.preview(batch.id(),0,20,ADMIN).content().getFirst();
        assertThat(row.errors()).anyMatch(message -> message.contains("ngoài I–V"));
        var cell=row.cells().stream().filter(c -> c.label().equals("Phân loại")).findFirst().orElseThrow();
        assertThatThrownBy(() -> corrections.correct(batch.id(),row.id(),new CorrectClassificationRequest(cell.key(),"II","",row.rowVersion()),ADMIN)).hasMessageContaining("lý do");
        corrections.correct(batch.id(),row.id(),new CorrectClassificationRequest(cell.key(),"Ii.","Đã kiểm tra nguồn giả lập",row.rowVersion()),ADMIN);
        var corrected=imports.preview(batch.id(),0,20,ADMIN).content().getFirst();assertThat(corrected.errors()).isEmpty();
        assertThat(corrected.corrections()).hasSize(1);assertThat(corrected.corrections().getFirst().original()).isEqualTo("Lời hướng dẫn sai ô");
        assertThat(corrected.corrections().getFirst().corrected()).isEqualTo("II");
        assertThatThrownBy(() -> corrections.correct(batch.id(),row.id(),new CorrectClassificationRequest(cell.key(),"III","Không dùng bản cũ",row.rowVersion()),ADMIN)).isInstanceOf(com.booking.system.hr.api.HrApiException.class);
        writer.confirm(batch.id(),new ConfirmRequest(List.of(new RowDecision(corrected.id(),corrected.previewToken(),Map.of(),null))),ADMIN);
        assertThat(results.findAll()).anyMatch(value -> value.getRawValue().equals("II"));
        assertThat(rows.findById(row.id()).orElseThrow().getPayload()).contains("Lời hướng dẫn sai ô");
        assertThatThrownBy(() -> corrections.correct(batch.id(),row.id(),new CorrectClassificationRequest(cell.key(),"IV","Không sửa dòng đã nhập",corrected.rowVersion()),ADMIN)).hasMessageContaining("đang chờ");
    }
    @Test void confirmsOnlyTheYearOnExistingRowsWithoutChangingDatesPeopleOrOtherErrors() throws Exception {
        var batch=prepare(dataset("Phân loại giả lập sai"),false).batches().getFirst();
        var before=imports.preview(batch.id(),0,20,ADMIN).content().getFirst();
        assertThatThrownBy(() -> years.confirm(batch.id(),"",ADMIN)).hasMessageContaining("lý do");
        assertThatThrownBy(() -> years.confirm(batch.id(),"Đã xác minh nguồn giả lập",new HrImportActor("USER:staff","Fixture staff","HR_STAFF")))
            .isInstanceOf(com.booking.system.hr.api.HrApiException.class);
        assertThat(years.confirm(batch.id(),"Đã xác minh năm nguồn giả lập",ADMIN)).isEqualTo(1);
        var after=imports.preview(batch.id(),0,20,ADMIN).content().getFirst();
        assertThat(after.errors()).noneMatch(error -> error.equals(HealthBaselineYearConfirmation.UNVERIFIED_YEAR_ERROR));
        assertThat(after.errors()).anyMatch(error -> error.contains("ngoài I–V"));
        assertThat(after.examinedOn()).isNull();assertThat(after.employeeId()).isEqualTo(before.employeeId());
        assertThat(after.cells()).isEqualTo(before.cells());assertThat(after.previewToken()).isNotEqualTo(before.previewToken());
        assertThat(after.corrections()).anyMatch(item -> item.label().equals("Năm khám") && item.corrected().equals("2023"));
        assertThat(years.confirm(batch.id(),"Chạy lại xác nhận",ADMIN)).isZero();
        assertThat(imports.preview(batch.id(),0,20,ADMIN).content().getFirst().rowVersion()).isEqualTo(after.rowVersion());
        assertThat(results.count()).isZero();
    }
    @Test void refusesYearMismatchAndPreservesTheOriginalPayload() throws Exception {
        var batch=prepare(dataset("II"),false).batches().getFirst();
        var row=rows.findByBatchIdOrderBySourceRow(batch.id(),org.springframework.data.domain.PageRequest.of(0,20)).getContent().getFirst();
        var value=HealthImportSupport.read(row.getPayload(),HealthParsedData.Row.class);
        row.setPayload(HealthImportSupport.json(new HealthParsedData.Row(value.sourceRow(),value.code(),value.name(),value.birth(),"010124-BASE",value.date(),value.values(),value.errors(),value.warnings())));
        rows.saveAndFlush(row);String original=row.getPayload();
        assertThatThrownBy(() -> years.confirm(batch.id(),"Đã kiểm tra nhưng không khớp",ADMIN)).hasMessageContaining("không khớp");
        assertThat(rows.findById(row.getId()).orElseThrow().getPayload()).isEqualTo(original);
        assertThat(results.count()).isZero();
    }
}
