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
 "logging.level.org.springframework.web=INFO","logging.level.org.hibernate.SQL=OFF"})
@AutoConfigureMockMvc
class HealthRecordTest {
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
    @Test void peopleAreFilteredSortedAndPaginatedBeforeReturningPrivateProjections(){
        var first=service.people("RECORDDEV",null,null,"ALL","codeDesc",0,1,ADMIN);assertThat(first.totalElements()).isEqualTo(2);assertThat(first.content()).extracting(Person::employeeId).containsExactly(otherId);
        assertThat(service.people("RECORDDEV",2024,HrWorkforceGroup.OFFICE,"WITH_DATA","codeAsc",0,20,ADMIN).content()).singleElement().satisfies(p->assertThat(p.visitCount()).isEqualTo(2));
        assertThat(service.people("RECORDDEV",2026,null,"WITHOUT_DATA","codeAsc",0,20,ADMIN).totalElements()).isEqualTo(2);
        assertThat(service.people("RECORDDEV",2026,null,"WITH_DATA","codeAsc",0,20,ADMIN).content()).isEmpty();
        assertThat(service.people("%",null,null,"ALL","codeAsc",0,20,ADMIN).content()).isEmpty();
        assertThatThrownBy(()->service.people("",null,null,"ALL","code;drop",0,20,ADMIN)).hasMessageContaining("không hợp lệ");
        assertThatThrownBy(()->service.people("",null,null,"ALL","codeAsc",0,101,ADMIN)).hasMessageContaining("100");
    }
    @Test void timelinePreservesAllVisitsAndMakesAbsentYearsExplicit(){
        var profile=service.timeline(employeeId,2023,2026,0,20,ADMIN);assertThat(profile.visits().totalElements()).isEqualTo(4);
        assertThat(profile.years()).extracting(YearCount::visits).containsExactly(1L,2L,1L,0L);
        assertThat(profile.visits().content()).allMatch(v->v.status().equals("REVIEW"));
        assertThat(service.timeline(otherId,2023,2026,0,20,ADMIN).visits().content()).isEmpty();
        assertThatThrownBy(()->service.timeline(employeeId,2026,2023,0,20,ADMIN)).hasMessageContaining("khoảng");
        assertThatThrownBy(()->service.timeline(employeeId,2000,2026,0,20,ADMIN)).hasMessageContaining("10 năm");
    }
    @Test void resultsPreserveReferencesAndSourceButCannotCrossPeople(){
        var page=service.results(employeeId,visitIds.get(0),"NUMBER",0,1,ADMIN);assertThat(page.results().content()).singleElement().satisfies(v->{assertThat(v.raw()).isEqualTo("10");assertThat(v.reference()).isEqualTo("Khoảng nguồn giả lập");assertThat(v.sourceColumn()).isEqualTo("D");});
        assertThatThrownBy(()->service.results(otherId,visitIds.getFirst(),"",0,20,ADMIN)).hasMessageContaining("trong phạm vi");
        assertThatThrownBy(()->service.compare(otherId,new CompareRequest(visitIds.subList(0,2),"",0,20),ADMIN)).hasMessageContaining("trong phạm vi");
    }
    @Test void comparisonSeparatesMethodsKeepsMissingCellsAndOnlyCalculatesCompatibleExactNumbers(){
        var data=service.compare(employeeId,new CompareRequest(List.of(visitIds.get(0),visitIds.get(3)),"",0,20),ADMIN);
        assertThat(data.rows().content()).hasSize(3);
        var compatible=data.rows().content().stream().filter(r->r.indicator().method().equals("Máy A")).findFirst().orElseThrow();
        assertThat(compatible.numericComparable()).isTrue();assertThat(compatible.difference()).isEqualByComparingTo("3");
        var limit=data.rows().content().stream().filter(r->r.indicator().method().equals("Máy B")).findFirst().orElseThrow();
        assertThat(limit.numericComparable()).isFalse();assertThat(limit.difference()).isNull();assertThat(limit.cells().getFirst().missing()).isTrue();assertThat(limit.cells().getLast().raw()).isEqualTo("<0.5");
        var textRow=data.rows().content().stream().filter(r->r.indicator().type()==HealthTypes.IndicatorType.TEXT).findFirst().orElseThrow();assertThat(textRow.numericComparable()).isFalse();
        assertThatThrownBy(()->service.compare(employeeId,new CompareRequest(List.of(visitIds.get(0),visitIds.get(0)),"",0,20),ADMIN)).hasMessageContaining("khác nhau");
    }
    @Test void missingDatesWithinSameYearNeverInventChronology(){
        var changed=visits.findAllById(visitIds.subList(1,3));changed.forEach(v->v.setExaminedOn(null));visits.saveAllAndFlush(changed);
        var data=service.compare(employeeId,new CompareRequest(visitIds.subList(1,3),"NUMBER",0,20),ADMIN);assertThat(data.rows().content()).singleElement().satisfies(r->{assertThat(r.numericComparable()).isFalse();assertThat(r.note()).contains("chưa đủ ngày");});
    }
    @Test void listAndTimelineQueryCountsDoNotGrowWithPeopleOrVisits(){
        var stats=emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();stats.clear();service.people("RECORDDEV",null,null,"ALL","codeAsc",0,1,ADMIN);long small=stats.getPrepareStatementCount();
        stats.clear();service.people("RECORDDEV",null,null,"ALL","codeAsc",0,20,ADMIN);assertThat(stats.getPrepareStatementCount()).isEqualTo(small).isLessThanOrEqualTo(2);
        stats.clear();service.timeline(employeeId,2023,2026,0,20,ADMIN);assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(5);
    }
    @Test void classificationUsesTheStoredSourceAndDoesNotChooseBetweenConflictingValues(){
        var classification=indicator("HEALTH_CLASSIFICATION",HealthTypes.IndicatorType.CODED);var visit=visits.findById(visitIds.getFirst()).orElseThrow();
        results.saveAndFlush(result(visit,classification,"II.","",""));
        assertThat(service.timeline(employeeId,2023,2023,0,20,ADMIN).visits().content().getFirst().classification()).isEqualTo("II.");
        results.saveAndFlush(result(visit,classification,"III","","Nguồn bổ sung giả lập"));
        assertThat(service.timeline(employeeId,2023,2023,0,20,ADMIN).visits().content().getFirst().classification()).contains("Nhiều giá trị");
    }
    @Test void qualifiedOrLocaleFormattedNumbersAreNotReinterpretedAsExactValues(){
        var item=results.findByVisitIdIn(List.of(visitIds.getFirst())).stream().filter(r->r.getIndicatorId().equals(number.getId())).findFirst().orElseThrow();
        for(String raw:List.of("<0.5","1,000",">=12","120/80","1234567890123456")){
            item.setRawValue(raw);item=results.saveAndFlush(item);
            var comparison=service.compare(employeeId,new CompareRequest(List.of(visitIds.getFirst(),visitIds.getLast()),"NUMBER",0,20),ADMIN);
            assertThat(comparison.rows().content()).filteredOn(r->r.indicator().method().equals("Máy A")).singleElement().satisfies(r->{assertThat(r.numericComparable()).isFalse();assertThat(r.difference()).isNull();assertThat(r.cells().getFirst().raw()).isEqualTo(raw);});
        }
    }
    @Test void recordsAndComparisonRequireManagersReturnNoStoreAndValidateBodies()throws Exception{
        String root="/api/v1/hr/health/records";
        mvc.perform(get(root)).andExpect(status().isUnauthorized());mvc.perform(get(root).header("Authorization","Bearer HR_STAFF")).andExpect(status().isForbidden());
        mvc.perform(get(root).param("keyword","RECORDDEV").header("Authorization","Bearer MANAGER")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data.content[0].identity").doesNotExist());
        mvc.perform(get(root+"/"+employeeId+"/timeline").param("fromYear","2023").param("toYear","2026").header("Authorization","Bearer ADMIN")).andExpect(status().isOk()).andExpect(jsonPath("$.data.years[3].visits").value(0));
        mvc.perform(get(root+"/"+employeeId+"/timeline").header("Authorization","Bearer ADMIN")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Bộ lọc sổ sức khỏe không hợp lệ."));
        String url=root+"/"+employeeId+"/compare";
        mvc.perform(post(url).contentType("application/json").content("{\"visitIds\":[],\"page\":0,\"size\":20}").header("Authorization","Bearer ADMIN")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        mvc.perform(post(url).contentType("application/json").content("{\"visitIds\":[],\"page\":0,\"size\":20}").header("Authorization","Bearer HR_STAFF")).andExpect(status().isForbidden());
        assertThatThrownBy(()->service.timeline(employeeId,2023,2026,0,20,new HrImportActor("USER:staff","Fixture","HR_STAFF"))).isInstanceOf(com.booking.system.hr.api.HrApiException.class);
        assertThat(jdbc.queryForObject("select count(*) from hr_health_publications",Integer.class)).isZero();
    }
}
