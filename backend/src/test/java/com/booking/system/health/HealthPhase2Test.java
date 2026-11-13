package com.booking.system.health;

import com.booking.system.health.api.*;
import com.booking.system.health.api.HrHealthImportDtos.*;
import com.booking.system.health.entity.*;
import com.booking.system.health.importer.*;
import com.booking.system.health.repository.*;
import com.booking.system.health.service.*;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.repository.HrEmployeeRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes=HealthPhase2Test.Config.class,properties={
 "spring.datasource.url=jdbc:h2:mem:health_phase2;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false","spring.flyway.enabled=true","cfc.health.enabled=true",
 "logging.level.org.springframework.web=INFO","logging.level.org.hibernate.SQL=OFF"})
@AutoConfigureMockMvc
class HealthPhase2Test {
    static final HrImportActor ADMIN=new HrImportActor("USER:health-dev","Fixture Admin","ADMIN");
    @jakarta.annotation.Resource HealthWorkbookParser parser;
    @jakarta.annotation.Resource HealthImportService imports;
    @jakarta.annotation.Resource HealthImportWriter writer;
    @jakarta.annotation.Resource HealthCampaignService campaigns;
    @jakarta.annotation.Resource HealthIndicatorService indicators;
    @jakarta.annotation.Resource HealthImportRowRepository rows;
    @jakarta.annotation.Resource HealthResultRepository results;
    @jakarta.annotation.Resource HealthResultChangeRepository changes;
    @jakarta.annotation.Resource HealthEmployeeRepository people;
    @jakarta.annotation.Resource JdbcTemplate jdbc;
    @jakarta.annotation.Resource MockMvc mvc;
    @jakarta.annotation.Resource com.booking.system.security.JwtUtils tokens;
    @jakarta.annotation.Resource com.booking.system.repository.UserRepository users;
    String campaignId,indicatorId,employeeId;

    @BeforeEach void fixtures() {
        for (String table : List.of("result_changes","results","publications","import_rows","imports","visits","subjects","mapping_profiles","indicator_aliases","indicators","campaigns","audit_events")) jdbc.update("delete from hr_health_"+table);
        jdbc.update("delete from hr_employees where employee_code like 'HEALTHDEV%'");
        HrEmployee e=new HrEmployee(); e.setEmployeeCode("HEALTHDEV01");e.setFullName("Nhân viên giả lập");e.setCreatedByActor(ADMIN.subject());e.setUpdatedByActor(ADMIN.subject());
        employeeId=people.saveAndFlush(e).getId();
        campaignId=campaigns.create(new HrHealthDtos.CampaignRequest("DEV2026","Đợt giả lập",2026,"Bệnh viện giả lập",null,null,HealthTypes.WorkforceGroup.BOTH,null),ADMIN).id();
        indicatorId=indicators.create(new HrHealthDtos.IndicatorRequest("AST_DEV","AST giả lập","Sinh hóa",HealthTypes.IndicatorType.NUMBER,HealthTypes.IndicatorStatus.ACTIVE,List.of("AST (máy A)"),null),ADMIN).id();
    }
    private static final Map<String,byte[]> WORKBOOKS=new HashMap<>();
    static byte[] book(String[][] data) throws Exception {
        String key=Arrays.deepToString(data);
        if (WORKBOOKS.containsKey(key)) return WORKBOOKS.get(key);
        try (var workbook=new XSSFWorkbook(); var out=new ByteArrayOutputStream()) {
            var sheet=workbook.createSheet("Kết quả");
            String[] headers={"Họ tên","Mã nhân viên","SID","AST (máy A)","AST - Giới hạn b/t"};
            sheet.createRow(0);
            var header=sheet.getRow(0); for (int col=0;col<headers.length;col++) header.createCell(col).setCellValue(headers[col]);
            for (int r=0;r<data.length;r++) { var row=sheet.createRow(r+1); for (int c=0;c<data[r].length;c++) row.createCell(c).setCellValue(data[r][c]); }
            workbook.write(out); byte[] bytes=out.toByteArray(); WORKBOOKS.put(key,bytes); return bytes;
        }
    }
    StageRequest mapping(String profile) {
        return new StageRequest(campaignId,"Kết quả",1,1,"DEV_HOSPITAL",false,LocalDate.of(2026,1,1),Map.of(
            0,new Mapping(Field.NAME,null,null,null),1,new Mapping(Field.EMPLOYEE_CODE,null,null,null),2,new Mapping(Field.SID,null,null,null),
            3,new Mapping(Field.RESULT,indicatorId,"U/L","máy A"),4,new Mapping(Field.REFERENCE,indicatorId,"U/L","máy A")),profile);
    }
    Batch stage(String value,String sid) throws Exception { return imports.stage(book(new String[][]{{"Nhân viên giả lập","HEALTHDEV01",sid,value,"<34"}}),mapping(null),ADMIN); }
    PreviewRow first(Batch batch) { return imports.preview(batch.id(),0,20,ADMIN).content().getFirst(); }
    ConfirmRequest decision(PreviewRow row,Map<String,Choice> choices,String reason) { return new ConfirmRequest(List.of(new RowDecision(row.id(),row.previewToken(),choices,reason))); }

    @Test void inspectRecognizesHeadersAndAliasWithoutUsingCodeAsEmployeeId() throws Exception {
        byte[] file=book(new String[][]{{"Nhân viên giả lập","HEALTHDEV01","SID-1","20","<34"}});
        var inspection=imports.inspect(file,ADMIN); var sheet=inspection.sheets().getFirst();
        assertThat(sheet.headerRow()).isEqualTo(1); assertThat(sheet.dataRows()).isEqualTo(1);
        assertThat(sheet.columns().get(3).indicatorId()).isEqualTo(indicatorId);
        assertThat(HealthWorkbookParser.suggest("Code")).isEqualTo(Field.IGNORE);
    }
    @Test void stagesWithoutWritingResultsThenConfirmsAndRetriesWithoutDuplicates() throws Exception {
        var batch=stage("20","SID-1"); assertThat(results.count()).isZero();
        var row=first(batch);assertThat(row.errors()).isEmpty();assertThat(row.employeeId()).isEqualTo(employeeId);
        var confirmed=writer.confirm(batch.id(),decision(row,Map.of(),null),ADMIN);
        assertThat(confirmed.importedRows()).isEqualTo(1);assertThat(results.count()).isEqualTo(1);
        assertThat(writer.confirm(batch.id(),decision(row,Map.of(),null),ADMIN).skippedRows()).isEqualTo(1);
        assertThat(results.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from hr_health_publications",Integer.class)).isZero();
    }
    @Test void duplicateFileIsBlockedButDifferentSupplementCanBeReviewed() throws Exception {
        var batch=stage("20","SID-1");
        assertThatThrownBy(() -> stage("20","SID-1")).isInstanceOf(com.booking.system.hr.api.HrApiException.class);
        writer.confirm(batch.id(),decision(first(batch),Map.of(),null),ADMIN);
        var supplement=stage("22","SID-1");var row=first(supplement);
        assertThat(row.cells().getFirst().action()).isEqualTo("CONFLICT");
        assertThatThrownBy(() -> writer.confirm(supplement.id(),decision(row,Map.of(),null),ADMIN)).hasMessageContaining("từng ô");
        assertThat(results.findAll().getFirst().getRawValue()).isEqualTo("20");
        writer.confirm(supplement.id(),decision(row,Map.of(row.cells().getFirst().key(),Choice.REPLACE),"Đính chính nguồn giả lập"),ADMIN);
        assertThat(results.count()).isEqualTo(1);assertThat(changes.count()).isEqualTo(1);
        assertThat(results.findAll().getFirst().getRawValue()).isEqualTo("22");
        assertThat(changes.findAll().getFirst().getBeforePayload()).contains("20");
    }
    @Test void stalePreviewPreventsOverwritingNewerSupplement() throws Exception {
        var original=stage("20","SID-1");writer.confirm(original.id(),decision(first(original),Map.of(),null),ADMIN);
        var b=stage("21","SID-1");var stale=first(b);var c=stage("22","SID-1");var fresh=first(c);
        writer.confirm(c.id(),decision(fresh,Map.of(fresh.cells().getFirst().key(),Choice.REPLACE),"Nguồn mới"),ADMIN);
        assertThatThrownBy(() -> writer.confirm(b.id(),decision(stale,Map.of(stale.cells().getFirst().key(),Choice.REPLACE),"Nguồn cũ"),ADMIN))
            .hasMessageContaining("sau xem trước");
        assertThat(results.findAll().getFirst().getRawValue()).isEqualTo("22");
    }
    @Test void manualResolutionIsRequiredWhenCodeMissingAndPartialCancelPreservesImportedResults() throws Exception {
        var batch=imports.stage(book(new String[][]{{"Nhân viên giả lập","HEALTHDEV01","SID-1","20","<34"},
                {"Nhân viên giả lập","","SID-2","21","<34"}}),mapping(null),ADMIN);
        var page=imports.preview(batch.id(),0,20,ADMIN);var first=page.content().getFirst();var second=page.content().get(1);
        assertThat(second.employeeId()).isNull();assertThat(second.candidates()).hasSize(1);
        var imported=writer.confirm(batch.id(),decision(first,Map.of(),null),ADMIN);
        assertThat(imported.batch().status()).isEqualTo("PARTIAL");
        var cancelled=writer.cancel(batch.id(),new VersionRequest(imported.batch().rowVersion()),ADMIN);
        assertThat(cancelled.pendingRows()).isZero();assertThat(results.count()).isEqualTo(1);
        assertThat(rows.findById(second.id()).orElseThrow().getPayload()).isNull();
    }
    @Test void resolvesMissingEmployeeAndKeepsExistingValueWhenRequested() throws Exception {
        var a=stage("20","SID-1");writer.confirm(a.id(),decision(first(a),Map.of(),null),ADMIN);
        var batch=imports.stage(book(new String[][]{{"Tên nguồn khác","","SID-1","99","<34"}}),mapping(null),ADMIN);
        var pending=first(batch);assertThat(pending.employeeId()).isNull();
        writer.resolve(batch.id(),pending.id(),new ResolveRequest(employeeId,"Đã đối chiếu hồ sơ giả lập",pending.rowVersion()),ADMIN);
        var row=first(batch);assertThat(row.errors()).isEmpty();
        writer.confirm(batch.id(),decision(row,Map.of(row.cells().getFirst().key(),Choice.KEEP),null),ADMIN);
        assertThat(results.findAll().getFirst().getRawValue()).isEqualTo("20");assertThat(changes.count()).isZero();
    }
    @Test void noSidRequiresDateAndSourceSidCannotCrossPeople() throws Exception {
        var a=stage("20","SID-1");writer.confirm(a.id(),decision(first(a),Map.of(),null),ADMIN);
        var b=stage("22","SID-1");var row=first(b);
        HrEmployee e=new HrEmployee();e.setEmployeeCode("HEALTHDEV02");e.setFullName("Người giả lập khác");e.setCreatedByActor(ADMIN.subject());e.setUpdatedByActor(ADMIN.subject());
        String other=people.saveAndFlush(e).getId();writer.resolve(b.id(),row.id(),new ResolveRequest(other,"Đối chiếu thử trường hợp sai",row.rowVersion()),ADMIN);
        assertThat(first(b).errors()).anyMatch(error -> error.contains("người hoặc đợt"));
        assertThatThrownBy(() -> writer.confirm(b.id(),decision(first(b),Map.of(),null),ADMIN)).hasMessageContaining("còn lỗi");
    }
    @Test void rejectedRowsRemainPendingAndCancelledFileCanBeMappedAgain() throws Exception {
        var batch=stage("20","SID-1");var cancelled=writer.cancel(batch.id(),new VersionRequest(batch.rowVersion()),ADMIN);
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(stage("20","SID-1").status()).isEqualTo("REVIEW");
    }
    @Test void profilesPreserveStructureAndInspectionDoesNotPersistSourceData() throws Exception {
        byte[] data=book(new String[][]{{"Nhân viên giả lập","HEALTHDEV01","SID-1","20","<34"}});
        var sheet=imports.inspect(data,ADMIN).sheets().getFirst();
        imports.stage(data,mapping("Mẫu thử nghiệm"),ADMIN);
        assertThat(imports.profiles(sheet.fingerprint(),0,20,ADMIN).content()).hasSize(1);
        assertThat(imports.profiles(HealthImportSupport.hash("changed-header"),0,20,ADMIN).content()).isEmpty();
        assertThat(results.count()).isZero();
    }
    @Test void guestsAndHrStaffCannotUseImportOrEmployeeLookupEndpoints() throws Exception {
        mvc.perform(get("/api/v1/hr/health/imports/employees?keyword=HEALTHDEV")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/hr/health/imports/inspect")).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> imports.inspect(book(new String[][]{}),new HrImportActor("USER:staff","Test","HR_STAFF")))
            .isInstanceOf(com.booking.system.hr.api.HrApiException.class);
    }
    @Test void parserBlocksInvalidArchivesAndDuplicateSidsAndKeepsTextComparators() throws Exception {
        assertThatThrownBy(() -> parser.inspect(new byte[]{1,2,3})).hasMessageContaining("xlsx");
        var batch=imports.stage(book(new String[][]{{"Nhân viên giả lập","HEALTHDEV01","SID-1","<0.5","<34"},
                {"Nhân viên giả lập","HEALTHDEV01","SID-1","Âm tính","<34"}}),mapping(null),ADMIN);
        var page=imports.preview(batch.id(),0,20,ADMIN);
        assertThat(page.content()).allSatisfy(row -> assertThat(row.errors()).anyMatch(message -> message.contains("SID lặp")));
        assertThat(page.content().getFirst().cells().getFirst().incoming()).isEqualTo("<0.5");
    }
    @Test void referencesMustMatchAResultAndBlankSupplementDoesNotEraseExistingValues() throws Exception {
        var config=mapping(null);var columns=new HashMap<>(config.columns());
        columns.put(4,new Mapping(Field.REFERENCE,indicatorId,"different unit","máy A"));
        var invalid=new StageRequest(config.campaignId(),config.sheet(),1,1,config.sourceNamespace(),false,config.defaultDate(),columns,null);
        assertThatThrownBy(() -> imports.stage(book(new String[][]{{"Nhân viên giả lập","HEALTHDEV01","SID-1","20","<34"}}),invalid,ADMIN))
            .hasMessageContaining("Khoảng tham chiếu");
        var a=stage("20","SID-1");writer.confirm(a.id(),decision(first(a),Map.of(),null),ADMIN);
        var b=stage("","SID-1");assertThat(first(b).errors()).anyMatch(value -> value.contains("Không có kết quả"));
        assertThatThrownBy(() -> writer.confirm(b.id(),decision(first(b),Map.of(),null),ADMIN)).hasMessageContaining("còn lỗi");
        assertThat(results.findAll().getFirst().getRawValue()).isEqualTo("20");
    }
    @Test void explicitHeaderOverridesAndSparseHiddenSheetsAreInspectedWithoutGuessingEmployeeCodes() throws Exception {
        try (var workbook=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var sheet=workbook.createSheet("Mẫu mới");sheet.createRow(0).createCell(0).setCellValue("Tiêu đề báo cáo");
            var header=sheet.createRow(2);header.createCell(0).setCellValue("Họ tên");header.createCell(1).setCellValue("Code");
            header.createCell(2).setCellValue("AST");sheet.createRow(3).createCell(2).setCellValue("máy A");
            var data=sheet.createRow(4);data.createCell(0).setCellValue("Người giả lập");data.createCell(2).setCellValue("<0.5");
            data.createCell(16381).setCellStyle(workbook.createCellStyle());workbook.setSheetHidden(0,true);
            workbook.write(out);byte[] file=out.toByteArray();
            var detected=parser.inspect(file).sheets().getFirst();assertThat(detected.headerRow()).isEqualTo(3);
            var selected=imports.inspect(file,"Mẫu mới",3,2,ADMIN).sheets().getFirst();
            assertThat(selected.hidden()).isTrue();assertThat(selected.dataRows()).isEqualTo(1);assertThat(selected.columns()).hasSize(3);
            assertThat(selected.columns().get(2).name()).isEqualTo("AST / máy A");
            assertThat(selected.fingerprint()).isNotEqualTo(detected.fingerprint());
            assertThat(selected.columns().get(1).suggested()).isEqualTo(Field.IGNORE);
            assertThatThrownBy(() -> parser.inspect(file,"Mẫu mới",0,4)).hasMessageContaining("Tiêu đề");
            data.getCell(2).setCellFormula("1/0");data.getCell(2).setCellErrorValue(org.apache.poi.ss.usermodel.FormulaError.DIV0.getCode());
            out.reset();workbook.write(out);
            var config=new StageRequest(campaignId,"Mẫu mới",3,2,"DEV_HOSPITAL",false,null,Map.of(
                0,new Mapping(Field.NAME,null,null,null),1,new Mapping(Field.IGNORE,null,null,null),2,new Mapping(Field.RESULT,indicatorId,null,null)),null);
            assertThat(parser.parse(out.toByteArray(),config).rows().getFirst().errors()).anyMatch(message -> message.contains("lỗi Excel"));
        }
    }
    @Test void noSidAndUnverifiedOrWrongYearDatesRequireReview() throws Exception {
        var config=mapping(null);
        var noDate=new StageRequest(campaignId,config.sheet(),1,1,config.sourceNamespace(),false,null,config.columns(),null);
        var batch=imports.stage(book(new String[][]{{"Nhân viên giả lập","HEALTHDEV01","","20","<34"}}),noDate,ADMIN);
        assertThat(first(batch).errors()).isNotEmpty();
        var wrong=imports.stage(book(new String[][]{{"Nhân viên giả lập","HEALTHDEV01","010125-DEV","20","<34"}}),noDate,ADMIN);
        assertThat(first(wrong).errors()).isNotEmpty();assertThat(first(wrong).warnings()).isNotEmpty();
        var confirmedDate=new StageRequest(campaignId,config.sheet(),1,1,config.sourceNamespace(),true,null,config.columns(),null);
        var correct=imports.stage(book(new String[][]{{"Nhân viên giả lập","HEALTHDEV01","010126-DEV","20","<34"}}),confirmedDate,ADMIN);
        assertThat(first(correct).examinedOn()).isEqualTo(LocalDate.of(2026,1,1));assertThat(first(correct).errors()).isEmpty();
    }
    @Test void confirmationRollsBackAllSelectedRowsIfOneIsInvalid() throws Exception {
        var batch=imports.stage(book(new String[][]{{"Nhân viên giả lập","HEALTHDEV01","SID-1","20","<34"},
                {"Người không tồn tại","","SID-2","21","<34"}}),mapping(null),ADMIN);
        var preview=imports.preview(batch.id(),0,20,ADMIN).content();
        var decisions=preview.stream().map(row -> new RowDecision(row.id(),row.previewToken(),Map.<String,Choice>of(),null)).toList();
        assertThatThrownBy(() -> writer.confirm(batch.id(),new ConfirmRequest(decisions),ADMIN)).hasMessageContaining("còn lỗi");
        assertThat(results.count()).isZero();assertThat(rows.countByBatchIdAndState(batch.id(),"PENDING")).isEqualTo(2);
    }
    @Test void multipartApiValidatesManagersAndStoresOnlyAfterConfirmation() throws Exception {
        for (var role : List.of(com.booking.system.enums.RoleEnum.ADMIN,com.booking.system.enums.RoleEnum.MANAGER,com.booking.system.enums.RoleEnum.HR_STAFF)) {
            var user=new com.booking.system.entity.User();user.setId("phase2-"+role);user.setEmail(role+"@phase2.example.test");
            user.setRole(role);user.setFullName("Fixture "+role);user.setStatus(com.booking.system.enums.UserStatus.ACTIVE);
            org.mockito.Mockito.when(tokens.validateJwtToken(role.name())).thenReturn(true);
            org.mockito.Mockito.when(tokens.getEmailFromJwtToken(role.name())).thenReturn(user.getEmail());
            org.mockito.Mockito.when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        }
        byte[] data=book(new String[][]{{"Nhân viên giả lập","HEALTHDEV01","SID-API","20","<34"}});
        var file=new org.springframework.mock.web.MockMultipartFile("file","fixture.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",data);
        mvc.perform(multipart("/api/v1/hr/health/imports/inspect").file(file).header("Authorization","Bearer MANAGER"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data.sheets[0].dataRows").value(1));
        mvc.perform(multipart("/api/v1/hr/health/imports/inspect").file(file).param("headerRows","4").header("Authorization","Bearer ADMIN"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        mvc.perform(multipart("/api/v1/hr/health/imports/inspect").file(file).header("Authorization","Bearer HR_STAFF")).andExpect(status().isForbidden());
        var request=new org.springframework.mock.web.MockMultipartFile("request","request.json","application/json",HealthImportSupport.json(mapping(null)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/hr/health/imports").file(file).file(request).header("Authorization","Bearer ADMIN"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REVIEW"));
        assertThat(results.count()).isZero();
        mvc.perform(post("/api/v1/hr/health/imports/id/rows/row/correct-classification").header("Authorization","Bearer ADMIN").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        mvc.perform(post("/api/v1/hr/health/imports/id/rows/row/correct-classification").header("Authorization","Bearer HR_STAFF").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/hr/health/imports/id/confirm").header("Authorization","Bearer ADMIN").contentType("application/json").content("{\"rows\":[]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        mvc.perform(get("/api/v1/hr/health/imports").param("campaignId",campaignId).param("size","101").header("Authorization","Bearer ADMIN"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/hr/health/imports/employees").param("keyword","HEALTHDEV").header("Authorization","Bearer MANAGER"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].code").value("HEALTHDEV01"));
    }
    @Test void phase2ReadsUseDeclaredIndexesOnH2() {
        String rowsPlan=jdbc.queryForObject("EXPLAIN SELECT * FROM hr_health_import_rows WHERE batch_id='fixture' AND state='PENDING' ORDER BY source_row LIMIT 20",String.class);
        String importsPlan=jdbc.queryForObject("EXPLAIN SELECT * FROM hr_health_imports WHERE campaign_id='fixture' ORDER BY created_at DESC LIMIT 20",String.class);
        assertThat(rowsPlan).containsIgnoringCase("idx_health_row_batch_state");
        assertThat(importsPlan).containsIgnoringCase("fk_health_import_campaign_INDEX");
        System.out.println("HEALTH_PHASE2_H2_EXPLAIN_ROWS="+rowsPlan.replace('\n',' '));
        System.out.println("HEALTH_PHASE2_H2_EXPLAIN_IMPORTS="+importsPlan.replace('\n',' '));
    }
    @Test void emptyReferencesDoNotEraseStoredReferencesAndNoSidKeysNeedExplicitApproval() throws Exception {
        var original=stage("20","SID-1");writer.confirm(original.id(),decision(first(original),Map.of(),null),ADMIN);
        var update=imports.stage(book(new String[][]{{"Nhân viên giả lập","HEALTHDEV01","SID-1","21",""}}),mapping(null),ADMIN);
        var row=first(update);writer.confirm(update.id(),decision(row,Map.of(row.cells().getFirst().key(),Choice.REPLACE),"Đính chính số đo"),ADMIN);
        assertThat(results.findAll().getFirst().getReferenceText()).isEqualTo("<34");
        var config=mapping(null);var bytes=book(new String[][]{{"Nhân viên giả lập","HEALTHDEV01","","20","<34"}});
        var missing=imports.stage(bytes,config,ADMIN);assertThat(first(missing).errors()).anyMatch(e -> e.contains("chưa xác nhận khóa"));
        writer.cancel(missing.id(),new VersionRequest(missing.rowVersion()),ADMIN);
        var approved=new StageRequest(config.campaignId(),config.sheet(),1,1,config.sourceNamespace(),false,config.defaultDate(),true,config.columns(),null);
        var good=imports.stage(bytes,approved,ADMIN);assertThat(first(good).errors()).isEmpty();
        writer.confirm(good.id(),decision(first(good),Map.of(),null),ADMIN);assertThat(results.count()).isEqualTo(2);
    }
    @Test void pendingQueueFiltersAtTheDatabaseAndKeepsTheLegacyAllRowsDefault() throws Exception {
        var batch=stage("21","QUEUE-DEV");var row=first(batch);
        assertThat(imports.preview(batch.id(),0,1,"PENDING",ADMIN).totalElements()).isEqualTo(1);
        writer.confirm(batch.id(),decision(row,Map.of(),null),ADMIN);
        assertThat(imports.preview(batch.id(),0,1,"PENDING",ADMIN).content()).isEmpty();
        assertThat(imports.preview(batch.id(),0,1,"IMPORTED",ADMIN).content()).extracting(PreviewRow::id).containsExactly(row.id());
        assertThat(imports.preview(batch.id(),0,20,ADMIN).totalElements()).isEqualTo(1);
        assertThatThrownBy(()->imports.preview(batch.id(),0,20,"WRONG",ADMIN)).hasMessageContaining("không hợp lệ");
    }
    @Configuration(proxyBeanMethods=false)
    @Import({HealthPhase1Test.Config.class,HealthPersonalAccessController.class,HealthSelfController.class,com.booking.system.health.config.HealthSelfPrivacyFilter.class,
        com.booking.system.health.repository.HealthPersonalAccessRepository.class,com.booking.system.health.service.HealthPersonalAccessService.class,com.booking.system.health.service.HealthLookupLimiter.class,HealthImportController.class,HealthRecordController.class,HealthReportController.class,HealthPublicationController.class,
        com.booking.system.health.repository.HealthPublicationReadRepository.class,
        com.booking.system.health.service.HealthPublicationDrafts.class,com.booking.system.health.service.HealthPublicationReader.class,
        com.booking.system.health.service.HealthPublicationWriter.class,com.booking.system.health.service.HealthPublicationService.class,
        com.booking.system.health.service.HealthPublishedContentReader.class,com.booking.system.health.service.HealthSelfRecordService.class,com.booking.system.health.service.HealthPublishedReader.class,com.booking.system.health.service.HealthPublishedExport.class,com.booking.system.health.service.HealthCampaignStateService.class,
        com.booking.system.health.repository.HealthReportRepository.class,
        com.booking.system.health.service.HealthReportSnapshot.class,com.booking.system.health.service.HealthReportService.class,
        com.booking.system.health.repository.HealthRecordRepository.class,
        com.booking.system.health.service.HealthRecordService.class,com.booking.system.health.service.HealthRecordComparison.class})
    @ComponentScan(basePackageClasses=HealthImportService.class)
    static class Config { }
}
