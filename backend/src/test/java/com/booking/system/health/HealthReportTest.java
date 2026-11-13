package com.booking.system.health;

import com.booking.system.entity.User;
import com.booking.system.enums.*;
import com.booking.system.health.api.HrHealthRecordDtos.*;
import com.booking.system.health.entity.*;
import com.booking.system.health.repository.*;
import com.booking.system.health.service.*;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.enums.HrWorkforceGroup;
import com.booking.system.hr.importer.HrImportActor;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes=HealthPhase2Test.Config.class,properties={
 "spring.datasource.url=jdbc:h2:mem:health_record;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false","spring.flyway.enabled=true","cfc.health.enabled=true",
 "spring.jpa.properties.hibernate.generate_statistics=true","logging.level.org.hibernate.stat=OFF",
 "logging.level.org.springframework.jdbc=INFO","logging.level.org.springframework.web=INFO","logging.level.org.hibernate.SQL=OFF"})
@AutoConfigureMockMvc
class HealthReportTest {
    @jakarta.annotation.Resource HealthReportSnapshot snapshots;
    @jakarta.annotation.Resource HealthReportService reports;
    static final HrImportActor ADMIN=new HrImportActor("USER:record-fixture","Record fixture","ADMIN");
    @jakarta.annotation.Resource HealthRecordService service;
    @jakarta.annotation.Resource HealthEmployeeRepository employees;
    @jakarta.annotation.Resource HealthSubjectRepository subjects;
    @jakarta.annotation.Resource HealthVisitRepository visits;
    @jakarta.annotation.Resource HealthCampaignRepository campaigns;
    @jakarta.annotation.Resource HealthIndicatorRepository indicators;
    @jakarta.annotation.Resource HealthResultRepository results;
    @jakarta.annotation.Resource JdbcTemplate jdbc;
    @jakarta.annotation.Resource MockMvc mvc;
    @jakarta.annotation.Resource com.booking.system.security.JwtUtils tokens;
    @jakarta.annotation.Resource com.booking.system.repository.UserRepository users;
    @jakarta.annotation.Resource jakarta.persistence.EntityManagerFactory emf;
    String employeeId,otherId;List<String> visitIds;HealthIndicator number,text;
    @BeforeEach void setup() {
        for(String table:List.of("result_changes","results","publications","import_rows","imports","visits","subjects","mapping_profiles","indicator_aliases","indicators","campaigns","audit_events"))jdbc.update("delete from hr_health_"+table);
        jdbc.update("delete from hr_employees where employee_code like 'RECORDDEV%'");
        employeeId=employee("RECORDDEV01",HrWorkforceGroup.OFFICE);otherId=employee("RECORDDEV02",HrWorkforceGroup.GENERAL_LABOR);
        var subject=new HealthSubject();subject.setEmployeeId(employeeId);HealthAccess.audit(subject,ADMIN,true);subjects.saveAndFlush(subject);
        number=indicator("NUMBER_DEV",HealthTypes.IndicatorType.NUMBER);text=indicator("TEXT_DEV",HealthTypes.IndicatorType.TEXT);
        var added=new ArrayList<HealthVisit>();
        for(int n=0;n<4;n++) {
            int year=n==0?2023:n<3?2024:2025;var campaign=new HealthCampaign();campaign.setCode("RECORDDEV"+n);campaign.setName("Đợt khám giả lập "+n);
            campaign.setReportYear(year);campaign.setProvider("Cơ sở giả lập");campaign.setWorkforceGroup(HealthTypes.WorkforceGroup.BOTH);campaign.setStatus(HealthTypes.CampaignStatus.OPEN);HealthAccess.audit(campaign,ADMIN,true);campaigns.saveAndFlush(campaign);
            var visit=new HealthVisit();visit.setSubjectId(subject.getId());visit.setCampaignId(campaign.getId());visit.setExaminedOn(LocalDate.of(year,n==2?10:9,1));
            visit.setSourceNamespace("RECORD_DEV");visit.setSourceSid("FAKE-"+n);visit.setStatus("REVIEW");HealthAccess.audit(visit,ADMIN,true);added.add(visit);
        }
        visits.saveAllAndFlush(added);visitIds=added.stream().map(HealthVisit::getId).toList();
        var measurements=new ArrayList<HealthResult>();
        for(int n=0;n<4;n++){measurements.add(result(added.get(n),number,Integer.toString(10+n),"U/L","Máy A"));measurements.add(result(added.get(n),text,"Kết luận giả lập "+n,"",""));}
        measurements.add(result(added.get(3),number,"<0.5","U/L","Máy B"));results.saveAllAndFlush(measurements);
        for(String role:List.of("ADMIN","MANAGER","HR_STAFF")) {
            var user=new User();user.setId("record-user-"+role);user.setEmail(role.toLowerCase()+"@example.test");user.setFullName("Fixture "+role);user.setRole(RoleEnum.valueOf(role));user.setStatus(UserStatus.ACTIVE);
            when(tokens.validateJwtToken(role)).thenReturn(true);when(tokens.getEmailFromJwtToken(role)).thenReturn(user.getEmail());when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        }
    }
    String employee(String code,HrWorkforceGroup group){var e=new HrEmployee();e.setEmployeeCode(code);e.setFullName("Nhân viên hồ sơ giả lập "+code);e.setWorkforceGroup(group);e.setDateOfBirth(LocalDate.of(1990,1,2));HealthAccess.audit(e,ADMIN,true);return employees.saveAndFlush(e).getId();}
    HealthIndicator indicator(String code,HealthTypes.IndicatorType type){var i=new HealthIndicator();i.setCode(code);i.setName("Chỉ tiêu giả lập "+code);i.setCategory("Nhóm giả lập");i.setValueType(type);i.setStatus(HealthTypes.IndicatorStatus.ACTIVE);HealthAccess.audit(i,ADMIN,true);return indicators.saveAndFlush(i);}
    HealthResult result(HealthVisit visit,HealthIndicator indicator,String value,String unit,String method){var r=new HealthResult();r.setVisitId(visit.getId());r.setIndicatorId(indicator.getId());r.setRawValue(value);r.setUnit(unit);r.setMethod(method);r.setReferenceText("Khoảng nguồn giả lập");r.setSourceSheet("Sheet giả lập");r.setSourceRow(2);r.setSourceColumn("D");r.setSourceLabel(indicator.getName());r.setValueKey(com.booking.system.health.importer.HealthImportSupport.valueKey(indicator.getId(),unit,method));HealthAccess.audit(r,ADMIN,true);return r;}
    com.booking.system.health.api.HrHealthReportDtos.Request scope(String employee,int from,int to,String data,List<String> ids){return new com.booking.system.health.api.HrHealthReportDtos.Request(employee,from,to,employee==null?"RECORDDEV":null,null,data,"codeAsc",ids);}
    @Test void scopeStatisticsKeepPeopleVisitsMissingYearsAndMethodsSeparate() {
        var snapshot=snapshots.read(scope(null,2023,2026,"ALL",null),ADMIN);
        assertThat(snapshot.summary().people()).isEqualTo(2);assertThat(snapshot.summary().peopleWithData()).isEqualTo(1);
        assertThat(snapshot.summary().visits()).isEqualTo(4);assertThat(snapshot.summary().results()).isEqualTo(9);
        assertThat(snapshot.summary().counts()).anyMatch(c->c.label().equals("Người có dữ liệu năm 2026")&&c.count()==0&&c.denominator()==2);
        assertThat(snapshot.summary().counts()).anyMatch(c->c.label().equals("Lượt khám năm 2024")&&c.count()==2&&c.denominator()==4);
        assertThat(snapshots.read(scope(null,2024,2024,"WITHOUT_DATA",null),ADMIN).people()).extracting(Person::employeeId).containsExactly(otherId);
        assertThat(snapshots.read(scope(employeeId,2023,2025,"ALL",visitIds.subList(0,2)),ADMIN).visits()).hasSize(2);
    }
    @Test void excelPreservesRawValuesProvenancePrecisionAndNeverCreatesFormulas() throws Exception {
        var malicious=results.findAll().stream().filter(r->r.getIndicatorId().equals(text.getId())).findFirst().orElseThrow();malicious.setRawValue("=HYPERLINK(\"https://example.test\",\"giả lập\")");results.saveAndFlush(malicious);
        byte[] bytes=reports.export(scope(employeeId,2023,2026,"ALL",null),false,ADMIN);
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            assertThat(book.getNumberOfSheets()).isEqualTo(6);assertThat(book.getSheet("Ket qua").getLastRowNum()).isEqualTo(9);
            var values=new ArrayList<String>();for(var sheet:book)for(var row:sheet)for(var cell:row){assertThat(cell.getCellType()).isNotEqualTo(org.apache.poi.ss.usermodel.CellType.FORMULA);if(cell.getCellType()==org.apache.poi.ss.usermodel.CellType.STRING)values.add(cell.getStringCellValue());}
            assertThat(values).contains(malicious.getRawValue(),"<0.5","Khoảng nguồn giả lập","Sheet giả lập","2023-09-01");
            var comparisons=book.getSheet("So sanh");assertThat(comparisons.getLastRowNum()).isEqualTo(12);
            assertThat(values).contains("3","Không có dữ liệu");
        }
    }
    @Test void personalPdfEmbedsVietnameseAndAllRawResultsAcrossPages() throws Exception {
        byte[] bytes=reports.export(scope(employeeId,2023,2026,"ALL",null),true,ADMIN);
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(bytes)) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            String text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
            assertThat(text).contains("SỔ SỨC KHỎE","Nhân viên hồ sơ giả lập","<0.5","Sheet giả lập","Chưa có dữ liệu","2023-09-01");
            assertThat(text).doesNotContain("RECORDDEV02");
        }
    }
    @Test void reportEndpointsRequireManagersAndUsePrivateFileResponses() throws Exception {
        String body="{\"employeeId\":\""+employeeId+"\",\"fromYear\":2023,\"toYear\":2026,\"data\":\"ALL\",\"sort\":\"codeAsc\"}";
        for(String route:List.of("preview","excel","pdf")) {
            var request=post("/api/v1/hr/health/reports/"+route).contentType("application/json").content(body);
            mvc.perform(request).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/hr/health/reports/"+route).header("Authorization","Bearer HR_STAFF").contentType("application/json").content(body)).andExpect(status().isForbidden());
            var response=mvc.perform(post("/api/v1/hr/health/reports/"+route).header("Authorization","Bearer MANAGER").contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
            if(!route.equals("preview"))response.andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment;")));
        }
        mvc.perform(post("/api/v1/hr/health/reports/excel").header("Authorization","Bearer ADMIN").contentType("application/json").content(body.replace("2023","1800"))).andExpect(status().isBadRequest()).andExpect(jsonPath("status").value(400));
    }
    @Test void ownershipInvalidScopesAndPdfCollectionAreBlocked() {
        assertThatThrownBy(()->reports.export(scope(otherId,2023,2026,"ALL",visitIds.subList(0,2)),false,ADMIN)).hasMessageContaining("không thuộc");
        assertThatThrownBy(()->reports.export(scope(null,2023,2026,"ALL",null),true,ADMIN)).hasMessageContaining("một người");
        assertThatThrownBy(()->snapshots.read(scope(employeeId,2000,2026,"ALL",null),ADMIN)).hasMessageContaining("10 năm");
        assertThatThrownBy(()->snapshots.read(scope(employeeId,2023,2026,"ALL",List.of(visitIds.getFirst(),visitIds.getFirst())),ADMIN)).hasMessageContaining("khác nhau");
        assertThatThrownBy(()->snapshots.read(scope(employeeId,2023,2026,"ALL",null),new HrImportActor("x","x","HR_STAFF"))).hasMessageContaining("Chỉ quản trị");
    }
    @Test void snapshotUsesBatchQueriesAndExportNeverWritesOrPublishes() {
        var stats=emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();stats.clear();
        long before=results.count();stats.clear();snapshots.read(scope(null,2023,2026,"ALL",null),ADMIN);
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(3);
        reports.export(scope(employeeId,2023,2026,"ALL",null),false,ADMIN);
        assertThat(results.count()).isEqualTo(before);assertThat(jdbc.queryForObject("select count(*) from hr_health_publications",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from hr_health_audit_events",Integer.class)).isZero();
    }
    @Test void oversizedCellFailsExplicitlyInsteadOfTruncatingSource() {
        var result=results.findAll().getFirst();result.setRawValue("x".repeat(32768));results.saveAndFlush(result);
        assertThatThrownBy(()->reports.export(scope(employeeId,2023,2026,"ALL",null),false,ADMIN)).hasMessageContaining("không tự cắt");
    }
    @Test void excessivePeopleAreRejectedBeforeReadingTheirResults() {
        var added=new ArrayList<HrEmployee>();for(int n=0;n<500;n++){var e=new HrEmployee();e.setEmployeeCode("RECORDDEV-LIMIT-"+n);e.setFullName("Người giả lập giới hạn");HealthAccess.audit(e,ADMIN,true);added.add(e);}employees.saveAllAndFlush(added);
        assertThatThrownBy(()->snapshots.read(scope(null,2023,2026,"ALL",null),ADMIN)).hasMessageContaining("Vượt giới hạn");
    }
    @Test void fakeDemoArtifactsCanBeOpenedWithoutRealEmployeeData() throws Exception {
        var dir=java.nio.file.Path.of("/tmp/health-phase5-fixtures");java.nio.file.Files.createDirectories(dir);
        java.nio.file.Files.setPosixFilePermissions(dir,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        for(boolean pdf:List.of(false,true)) {
            var file=dir.resolve(pdf?"personal-fake.pdf":"collective-fake.xlsx");
            java.nio.file.Files.write(file,reports.export(scope(pdf?employeeId:null,2023,2026,"ALL",null),pdf,ADMIN));
            java.nio.file.Files.setPosixFilePermissions(file,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        }
    }
}
