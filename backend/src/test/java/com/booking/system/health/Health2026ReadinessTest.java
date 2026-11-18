package com.booking.system.health;

import com.booking.system.health.api.*;
import com.booking.system.health.api.HrHealthImportDtos.*;
import com.booking.system.health.api.HrHealthPublicationDtos.*;
import com.booking.system.health.entity.HealthTypes;
import com.booking.system.health.service.*;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.enums.HrWorkforceGroup;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static com.booking.system.health.Health2026WorkbookFixtures.*;

@SpringBootTest(classes=HealthPhase2Test.Config.class,properties={
 "spring.datasource.url=jdbc:h2:mem:health_2026_readiness;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false","spring.flyway.enabled=true","cfc.health.enabled=true",
 "logging.level.org.springframework.jdbc=INFO","logging.level.org.springframework.web=INFO","logging.level.org.hibernate.SQL=OFF"})
@AutoConfigureMockMvc
class Health2026ReadinessTest {
    @Autowired ApplicationContext context;
    @Autowired HealthRecordService records;
    @Autowired HealthReportService reports;
    @Autowired HealthPublicationReader publicationReader;
    @Autowired HealthPublicationService publications;
    @Autowired HealthPublishedReader published;
    HealthPhase2Test fixture;
    String extraId,otherId;
    final Map<Integer,String> years=new HashMap<>();

    @BeforeEach void setup() {
        fixture=new HealthPhase2Test();context.getAutowireCapableBeanFactory().autowireBean(fixture);fixture.fixtures();
        years.clear();years.put(2026,fixture.campaignId);
        for(int year:List.of(2024,2025))years.put(year,fixture.campaigns.create(new HrHealthDtos.CampaignRequest("QA"+year,"Đợt giả lập "+year,year,"Cơ sở giả lập",null,null,HealthTypes.WorkforceGroup.BOTH,null),HealthPhase2Test.ADMIN).id());
        extraId=fixture.indicators.create(new HrHealthDtos.IndicatorRequest("EXTRA_QA","Chỉ tiêu bổ sung giả lập","Nhóm giả lập",HealthTypes.IndicatorType.NUMBER,HealthTypes.IndicatorStatus.ACTIVE,List.of(),null),HealthPhase2Test.ADMIN).id();
        var employee=new HrEmployee();employee.setEmployeeCode("HEALTHDEV02");employee.setFullName("Công nhân giả lập");employee.setWorkforceGroup(HrWorkforceGroup.GENERAL_LABOR);HealthAccess.audit(employee,HealthPhase2Test.ADMIN,true);otherId=fixture.people.saveAndFlush(employee).getId();
    }
    StageRequest mapping(int year,boolean expanded,String method,String profile) {
        var columns=new LinkedHashMap<Integer,Mapping>();
        String[] roles=expanded?new String[]{"EMPLOYEE_CODE","RESULT","NAME","SID","EXAM_DATE","RESULT","REFERENCE"}
            :new String[]{"NAME","EMPLOYEE_CODE","SID","EXAM_DATE","RESULT","REFERENCE"};
        for(int i=0;i<roles.length;i++) {
            var role=Field.valueOf(roles[i]);boolean measurement=role==Field.RESULT||role==Field.REFERENCE;
            boolean extra=expanded&&i==1;
            columns.put(i,new Mapping(role,measurement?(extra?extraId:fixture.indicatorId):null,measurement?(extra?"đơn vị QA":"U/L"):null,measurement?(extra?"máy bổ sung":method):null));
        }
        return new StageRequest(years.get(year),"Kết quả",expanded?3:1,expanded?2:1,"HOSPITAL_QA",false,null,columns,profile);
    }
    Batch stage(int year,boolean expanded,List<Entry> entries) throws Exception {
        return fixture.imports.stage(workbook(expanded,entries),mapping(year,expanded,"máy A",null),HealthPhase2Test.ADMIN);
    }
    void confirmAll(Batch batch) {
        var decisions=fixture.imports.preview(batch.id(),0,100,HealthPhase2Test.ADMIN).content().stream()
            .map(row->new RowDecision(row.id(),row.previewToken(),Map.of(),null)).toList();
        assertThat(fixture.writer.confirm(batch.id(),new ConfirmRequest(decisions),HealthPhase2Test.ADMIN).importedRows()).isEqualTo(decisions.size());
    }
    void importHistory() throws Exception {
        for(int year:List.of(2024,2025))confirmAll(stage(year,false,List.of(entry(year,"HEALTHDEV01",year==2024?"20":"22",""))));
    }
    BatchRequest releaseRequest() {
        var preview=publicationReader.preview(new Scope(fixture.campaignId,"HEALTHDEV",null,null),HealthPhase2Test.ADMIN);
        return new BatchRequest(fixture.campaignId,preview.rows().stream().map(p->new PersonDecision(p.employeeId(),p.visits().stream().map(v->new Decision(v.visitId(),v.token())).toList())).toList(),"Rà soát nguồn giả lập",true);
    }
    void release() {
        assertThat(publications.apply(releaseRequest(),true,HealthPhase2Test.ADMIN).failed()).isZero();
        assertThat(publications.apply(releaseRequest(),false,HealthPhase2Test.ADMIN).failed()).isZero();
    }

    @Test void changedHospitalLayoutNeverReusesAnOlderProfileOrSilentlyDropsAddedColumns() throws Exception {
        byte[] old=workbook(false,List.of(entry(2025,"HEALTHDEV01","22","")));
        var oldSheet=fixture.imports.inspect(old,HealthPhase2Test.ADMIN).sheets().getFirst();
        fixture.imports.stage(old,mapping(2025,false,"máy A","Mẫu cũ giả lập"),HealthPhase2Test.ADMIN);
        byte[] next=workbook(true,List.of(entry(2026,"HEALTHDEV01","24","<0.5")));
        var newSheet=fixture.imports.inspect(next,"Kết quả",3,2,HealthPhase2Test.ADMIN).sheets().getFirst();
        assertThat(newSheet.fingerprint()).isNotEqualTo(oldSheet.fingerprint());
        assertThat(fixture.imports.profiles(newSheet.fingerprint(),0,20,HealthPhase2Test.ADMIN).content()).isEmpty();
        var current=mapping(2026,true,"máy A",null);var incomplete=new HashMap<>(current.columns());incomplete.remove(1);
        assertThatThrownBy(()->fixture.imports.stage(next,new StageRequest(current.campaignId(),current.sheet(),3,2,current.sourceNamespace(),false,null,incomplete,null),HealthPhase2Test.ADMIN)).hasMessageContaining("tất cả cột");
        confirmAll(fixture.imports.stage(next,current,HealthPhase2Test.ADMIN));
        assertThat(fixture.results.findAll()).extracting(r->r.getRawValue()).contains("<0.5","24");
    }

    @Test void compares2024To2026AndPreservesMissingHistoryAndOptionalTests() throws Exception {
        importHistory();confirmAll(stage(2026,true,List.of(entry(2026,"HEALTHDEV01","24","<0.5"),entry(2026,"HEALTHDEV02","25",""))));
        var timeline=records.timeline(fixture.employeeId,2024,2026,0,20,HealthPhase2Test.ADMIN);
        assertThat(timeline.years()).extracting(y->y.visits()).containsExactly(1L,1L,1L);
        var ids=timeline.visits().content().stream().map(v->v.id()).toList();
        var comparison=records.compare(fixture.employeeId,new HrHealthRecordDtos.CompareRequest(ids,null,0,100),HealthPhase2Test.ADMIN);
        var ast=comparison.rows().content().stream().filter(r->r.indicator().indicatorId().equals(fixture.indicatorId)).findFirst().orElseThrow();
        assertThat(ast.numericComparable()).isTrue();assertThat(ast.difference()).isEqualByComparingTo("4");
        var extra=comparison.rows().content().stream().filter(r->r.indicator().indicatorId().equals(extraId)).findFirst().orElseThrow();
        assertThat(extra.cells().stream().filter(c->c.missing()).count()).isEqualTo(2);
        assertThat(extra.numericComparable()).isFalse();assertThat(extra.difference()).isNull();
        var newcomer=records.timeline(otherId,2024,2026,0,20,HealthPhase2Test.ADMIN);
        assertThat(newcomer.years()).extracting(y->y.visits()).containsExactly(0L,0L,1L);
        assertThat(newcomer.visits().content()).hasSize(1);
    }

    @Test void unknownEmployeesAndInvalidDatesStayPendingWithoutInventedVisits() throws Exception {
        var batch=stage(2026,true,List.of(entry(2026,"KHONG-TON-TAI","24",""),new Entry("HEALTHDEV02","","QA-INVALID","31/02/2026","25","")));
        var rows=fixture.imports.preview(batch.id(),0,20,HealthPhase2Test.ADMIN).content();
        assertThat(rows).allSatisfy(row->assertThat(row.errors()).isNotEmpty());
        assertThat(fixture.results.count()).isZero();
        assertThatThrownBy(()->confirmAll(batch)).hasMessageContaining("còn lỗi");
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM hr_health_visits",Long.class)).isZero();
    }

    @Test void changedMethodCreatesASeparateComparisonRowInsteadOfAnUnjustifiedTrend() throws Exception {
        importHistory();var file=workbook(true,List.of(entry(2026,"HEALTHDEV01","24","")));
        confirmAll(fixture.imports.stage(file,mapping(2026,true,"máy B",null),HealthPhase2Test.ADMIN));
        var ids=records.timeline(fixture.employeeId,2024,2026,0,20,HealthPhase2Test.ADMIN).visits().content().stream().map(v->v.id()).toList();
        var rows=records.compare(fixture.employeeId,new HrHealthRecordDtos.CompareRequest(ids,null,0,100),HealthPhase2Test.ADMIN).rows().content();
        assertThat(rows).filteredOn(r->r.indicator().indicatorId().equals(fixture.indicatorId)).hasSize(2).allSatisfy(r->{assertThat(r.numericComparable()).isFalse();assertThat(r.difference()).isNull();});
    }

    @Test void lateCorrectionKeepsPublishedValuesUntilExplicitReviewAndRepublish() throws Exception {
        confirmAll(stage(2026,true,List.of(entry(2026,"HEALTHDEV01","24","<0.5"))));release();
        var before=publicationReader.preview(new Scope(fixture.campaignId,null,null,null),HealthPhase2Test.ADMIN).rows().getFirst().visits().getFirst().latest();
        var batch=stage(2026,true,List.of(entry(2026,"HEALTHDEV01","26","<0.5")));
        var row=fixture.imports.preview(batch.id(),0,20,HealthPhase2Test.ADMIN).content().getFirst();
        assertThat(row.cells()).anyMatch(c->c.action().equals("CONFLICT"));
        assertThatThrownBy(()->fixture.writer.confirm(batch.id(),new ConfirmRequest(List.of(new RowDecision(row.id(),row.previewToken(),Map.of(),null))),HealthPhase2Test.ADMIN)).hasMessageContaining("từng ô");
        var choices=new HashMap<String,Choice>();row.cells().stream().filter(c->c.action().equals("CONFLICT")).forEach(c->choices.put(c.key(),Choice.REPLACE));
        fixture.writer.confirm(batch.id(),new ConfirmRequest(List.of(new RowDecision(row.id(),row.previewToken(),choices,"Đính chính nguồn giả lập"))),HealthPhase2Test.ADMIN);
        assertThat(published.current(fixture.employeeId,before.id(),HealthPhase2Test.ADMIN).snapshot().results()).anyMatch(r->r.raw().equals("24"));
        assertThat(publications.apply(releaseRequest(),false,HealthPhase2Test.ADMIN).failed()).isEqualTo(1);
        release();
        var after=publicationReader.preview(new Scope(fixture.campaignId,null,null,null),HealthPhase2Test.ADMIN).rows().getFirst().visits().getFirst().latest();
        assertThat(after.revision()).isEqualTo(2);
        assertThat(published.current(fixture.employeeId,after.id(),HealthPhase2Test.ADMIN).snapshot().results()).anyMatch(r->r.raw().equals("26"));
        assertThatThrownBy(()->published.current(fixture.employeeId,before.id(),HealthPhase2Test.ADMIN)).hasMessageContaining("không còn");
    }

    @Test void exportsPersonalAndCollectionComparisonsAcrossThreeYearsWithoutPublishing() throws Exception {
        importHistory();confirmAll(stage(2026,true,List.of(entry(2026,"HEALTHDEV01","24","<0.5"),entry(2026,"HEALTHDEV02","25",""))));
        for(String owner:Arrays.asList(fixture.employeeId,null)) {
            var scope=new HrHealthReportDtos.Request(owner,2024,2026,owner==null?"HEALTHDEV":null,null,"ALL","codeAsc",null);
            var summary=reports.preview(scope,HealthPhase2Test.ADMIN);assertThat(summary.visits()).isEqualTo(owner==null?4:3);
            byte[] bytes=reports.export(scope,false,HealthPhase2Test.ADMIN);
            try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                String text=java.util.stream.StreamSupport.stream(workbook.spliterator(),false).flatMap(sheet->java.util.stream.StreamSupport.stream(sheet.spliterator(),false)).flatMap(r->java.util.stream.StreamSupport.stream(r.spliterator(),false)).map(Object::toString).collect(java.util.stream.Collectors.joining(" "));
                assertThat(text).contains("2024","2025","2026","<0.5");
            }
        }
        byte[] pdf=reports.export(new HrHealthReportDtos.Request(fixture.employeeId,2024,2026,null,null,"ALL","codeAsc",null),true,HealthPhase2Test.ADMIN);
        assertThat(new String(pdf,0,4,java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM hr_health_publications",Long.class)).isZero();
    }
}
