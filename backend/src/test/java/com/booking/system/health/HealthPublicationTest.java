package com.booking.system.health;

import com.booking.system.entity.User;
import com.booking.system.enums.*;
import com.booking.system.health.api.HrHealthPublicationDtos.*;
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
 "spring.datasource.url=jdbc:h2:mem:health_publication;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false","spring.flyway.enabled=true","cfc.health.enabled=true",
 "logging.level.org.springframework.web=INFO","logging.level.org.hibernate.SQL=OFF"})
@AutoConfigureMockMvc
class HealthPublicationTest {
    static final HrImportActor ADMIN=new HrImportActor("USER:publication-fixture","Publication fixture","ADMIN");
    @jakarta.annotation.Resource HealthPublicationReader reader;
    @jakarta.annotation.Resource HealthPublicationService service;
    @jakarta.annotation.Resource HealthPublicationWriter writer;
    @jakarta.annotation.Resource HealthPublishedReader published;
    @jakarta.annotation.Resource HealthPublishedExport exports;
    @jakarta.annotation.Resource HealthCampaignStateService states;
    @jakarta.annotation.Resource HealthEmployeeRepository employees;
    @jakarta.annotation.Resource HealthSubjectRepository subjects;
    @jakarta.annotation.Resource HealthVisitRepository visits;
    @jakarta.annotation.Resource HealthCampaignRepository campaigns;
    @jakarta.annotation.Resource HealthIndicatorRepository indicators;
    @jakarta.annotation.Resource HealthResultRepository results;
    @jakarta.annotation.Resource HealthPublicationRepository publications;
    @jakarta.annotation.Resource com.booking.system.health.importer.HealthImportService imports;
    @jakarta.annotation.Resource com.booking.system.health.importer.HealthImportWriter importWriter;
    @jakarta.annotation.Resource JdbcTemplate jdbc;
    @jakarta.annotation.Resource MockMvc mvc;
    @jakarta.annotation.Resource com.booking.system.security.JwtUtils tokens;
    @jakarta.annotation.Resource com.booking.system.repository.UserRepository users;
    String employeeId,otherId,campaignId;List<String> visitIds;HealthIndicator indicator;
    @BeforeEach void fixtures() {
        for(String table:List.of("result_changes","results","publications","import_rows","imports","visits","subjects","mapping_profiles","indicator_aliases","indicators","campaigns","audit_events"))jdbc.update("delete from hr_health_"+table);
        jdbc.update("delete from hr_employees where employee_code like 'PUBLICDEV%'");
        employeeId=employee("PUBLICDEV01");otherId=employee("PUBLICDEV02");
        var campaign=new HealthCampaign();campaign.setCode("PUBLICDEV2025");campaign.setName("Đợt công bố giả lập");campaign.setReportYear(2025);campaign.setProvider("Cơ sở giả lập");campaign.setWorkforceGroup(HealthTypes.WorkforceGroup.BOTH);campaign.setStatus(HealthTypes.CampaignStatus.OPEN);HealthAccess.audit(campaign,ADMIN,true);campaignId=campaigns.saveAndFlush(campaign).getId();
        indicator=new HealthIndicator();indicator.setCode("PUBLICDEV_RESULT");indicator.setName("Chỉ tiêu giả lập");indicator.setCategory("Nhóm giả lập");indicator.setValueType(HealthTypes.IndicatorType.TEXT);indicator.setStatus(HealthTypes.IndicatorStatus.ACTIVE);HealthAccess.audit(indicator,ADMIN,true);indicator=indicators.saveAndFlush(indicator);
        visitIds=List.of(visit(employeeId,"FAKE-PUB-1"),visit(employeeId,"FAKE-PUB-2"),visit(otherId,"FAKE-PUB-3"));
        for(String role:List.of("ADMIN","MANAGER","HR_STAFF")){var user=new User();user.setId("publication-user-"+role);user.setEmail(role.toLowerCase()+"@example.test");user.setFullName("Fixture "+role);user.setRole(RoleEnum.valueOf(role));user.setStatus(UserStatus.ACTIVE);when(tokens.validateJwtToken(role)).thenReturn(true);when(tokens.getEmailFromJwtToken(role)).thenReturn(user.getEmail());when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));}
    }
    String employee(String code){var e=new HrEmployee();e.setEmployeeCode(code);e.setFullName("Nhân viên công bố giả lập "+code);e.setWorkforceGroup(HrWorkforceGroup.OFFICE);e.setDateOfBirth(LocalDate.of(1990,1,2));HealthAccess.audit(e,ADMIN,true);return employees.saveAndFlush(e).getId();}
    String visit(String employee,String sid){var subject=subjects.findByEmployeeIdIn(List.of(employee)).stream().findFirst().orElseGet(()->{var s=new HealthSubject();s.setEmployeeId(employee);HealthAccess.audit(s,ADMIN,true);return subjects.saveAndFlush(s);});var v=new HealthVisit();v.setSubjectId(subject.getId());v.setCampaignId(campaignId);v.setSourceNamespace("PUBLIC_DEV");v.setSourceSid(sid);v.setStatus("REVIEW");HealthAccess.audit(v,ADMIN,true);v=visits.saveAndFlush(v);var r=new HealthResult();r.setVisitId(v.getId());r.setIndicatorId(indicator.getId());r.setRawValue("Kết luận giả lập <0.5");r.setValueKey(com.booking.system.health.importer.HealthImportSupport.valueKey(indicator.getId(),"",""));r.setSourceSheet("Sheet giả lập");r.setSourceRow(2);r.setSourceColumn("D");r.setSourceLabel("Chỉ tiêu nguồn giả lập");HealthAccess.audit(r,ADMIN,true);results.saveAndFlush(r);return v.getId();}
    Scope scope(){return new Scope(campaignId,"PUBLICDEV",null,null);}
    Preview preview(){return reader.preview(scope(),ADMIN);}
    BatchRequest request(Preview preview){return new BatchRequest(campaignId,preview.rows().stream().map(p->new PersonDecision(p.employeeId(),p.visits().stream().map(v->new Decision(v.visitId(),v.token())).toList())).toList(),"Đối chiếu nguồn giả lập",true);}
    Preview reviewed(){assertThat(service.apply(request(preview()),true,ADMIN).succeeded()).isEqualTo(2);return preview();}
    List<PublicationInfo> released(){assertThat(service.apply(request(reviewed()),false,ADMIN).succeeded()).isEqualTo(2);return preview().rows().stream().flatMap(p->p.visits().stream()).map(Candidate::latest).toList();}
    PublicationInfo releaseFor(String visit){return preview().rows().stream().flatMap(p->p.visits().stream()).filter(v->v.visitId().equals(visit)).findFirst().orElseThrow().latest();}
    @Test void previewIsReadOnlyAndRequiresExplicitReviewAndIncompleteAcknowledgement() {
        var p=preview();assertThat(p.people()).isEqualTo(2);assertThat(p.visits()).isEqualTo(3);assertThat(p.publishable()).isZero();assertThat(publications.count()).isZero();
        assertThat(service.apply(request(p),false,ADMIN).failed()).isEqualTo(2);
        var original=request(p);assertThat(service.apply(new BatchRequest(campaignId,original.people(),"Lý do giả lập",false),true,ADMIN).failed()).isEqualTo(2);
        assertThat(preview().publishable()).isZero();
        assertThat(reviewed().publishable()).isEqualTo(3);
    }
    @Test void publishCreatesImmutableSnapshotsAndRetriesDoNotDuplicateVersionsOrAudit() {
        var original=request(reviewed());var first=service.apply(original,false,ADMIN);assertThat(first.succeeded()).isEqualTo(2);assertThat(publications.count()).isEqualTo(3);
        int audit=jdbc.queryForObject("select count(*) from hr_health_audit_events",Integer.class);
        assertThat(service.apply(original,false,ADMIN).skipped()).isEqualTo(2);assertThat(publications.count()).isEqualTo(3);assertThat(jdbc.queryForObject("select count(*) from hr_health_audit_events",Integer.class)).isEqualTo(audit);
        var release=releaseFor(visitIds.getFirst());var raw=published.current(employeeId,release.id(),ADMIN).snapshot();assertThat(raw.results().getFirst().raw()).isEqualTo("Kết luận giả lập <0.5");
        var result=results.findByVisitIdIn(List.of(visitIds.getFirst())).getFirst();result.setRawValue("Kết luận bổ sung giả lập");results.saveAndFlush(result);
        assertThat(published.current(employeeId,release.id(),ADMIN).snapshot().results().getFirst().raw()).isEqualTo("Kết luận giả lập <0.5");
        var changed=preview().rows().getFirst().visits().stream().filter(v->v.visitId().equals(visitIds.getFirst())).findFirst().orElseThrow();assertThat(changed.state()).isEqualTo("CHANGED");assertThat(changed.reviewed()).isFalse();
        var stale=request(preview());assertThat(service.apply(stale,false,ADMIN).failed()).isEqualTo(1);
        service.apply(request(preview()),true,ADMIN);service.apply(request(preview()),false,ADMIN);
        var latest=releaseFor(visitIds.getFirst());assertThat(latest.revision()).isEqualTo(2);assertThatThrownBy(()->published.current(employeeId,release.id(),ADMIN)).hasMessageContaining("không còn");assertThat(published.current(employeeId,latest.id(),ADMIN).snapshot().results().getFirst().raw()).isEqualTo("Kết luận bổ sung giả lập");
    }
    @Test void aStaleVisitBlocksItsWholePersonWhileOtherPeopleStillCommit() {
        var p=reviewed();var result=results.findByVisitIdIn(List.of(visitIds.getFirst())).getFirst();result.setRawValue("Thay đổi sau xem trước");results.saveAndFlush(result);
        var outcome=service.apply(request(p),false,ADMIN);assertThat(outcome.succeeded()).isEqualTo(1);assertThat(outcome.failed()).isEqualTo(1);assertThat(publications.count()).isEqualTo(1);assertThat(publications.findAll().getFirst().getVisitId()).isEqualTo(visitIds.get(2));
    }
    @Test void revocationBlocksEveryContentAndFileRequestWithoutFallingBackToAnOlderRelease() {
        released();var release=releaseFor(visitIds.getFirst());
        writer.revoke(release.id(),new RevokeRequest(employeeId,release.rowVersion(),"Thu hồi giả lập"),ADMIN);
        assertThatThrownBy(()->published.current(employeeId,release.id(),ADMIN)).hasMessageContaining("không còn");
        assertThatThrownBy(()->exports.export(employeeId,release.id(),ADMIN)).hasMessageContaining("không còn");
        assertThat(writer.revoke(release.id(),new RevokeRequest(employeeId,release.rowVersion(),"Retry giả lập"),ADMIN).status()).isEqualTo("REVOKED");
        var request=request(preview());assertThat(service.apply(request,false,ADMIN).succeeded()).isEqualTo(1);assertThat(releaseFor(visitIds.getFirst()).revision()).isEqualTo(2);
        assertThatThrownBy(()->writer.revoke(release.id(),new RevokeRequest(employeeId,release.rowVersion(),"Cũ"),ADMIN)).hasMessageContaining("mới nhất");
    }
    @Test void locksAndReopeningDoNotPublishOrRevokeAndClosedCampaignBlocksReview() {
        var p=preview();var closed=states.set(campaignId,new CampaignStateRequest(p.campaign().rowVersion(),true,"Khóa giả lập"),ADMIN);assertThat(closed.status()).isEqualTo("CLOSED");assertThat(publications.count()).isZero();assertThat(service.apply(request(preview()),true,ADMIN).failed()).isEqualTo(2);
        states.set(campaignId,new CampaignStateRequest(closed.rowVersion(),false,"Mở giả lập"),ADMIN);var ready=reviewed();states.set(campaignId,new CampaignStateRequest(ready.campaign().rowVersion(),true,"Khóa lại"),ADMIN);
        assertThat(service.apply(request(preview()),false,ADMIN).succeeded()).isEqualTo(2);assertThat(publications.count()).isEqualTo(3);
        assertThat(service.revoke(request(preview()),ADMIN).succeeded()).isEqualTo(2);assertThat(publications.findAll()).allMatch(v->v.getStatus().equals("REVOKED"));
        assertThat(jdbc.queryForObject("select reason from hr_health_audit_events where action='REOPEN_HEALTH_CAMPAIGN'",String.class)).isEqualTo("Mở giả lập");
    }
    @Test void wrongPersonAndExpiredTokensCannotReleaseOrReadSomeoneElsesResults() {
        var p=reviewed();var first=p.rows().stream().filter(v->v.employeeId().equals(employeeId)).findFirst().orElseThrow();
        var wrong=new BatchRequest(campaignId,List.of(new PersonDecision(otherId,first.visits().stream().map(v->new Decision(v.visitId(),v.token())).toList())),"Sai người giả lập",true);
        assertThat(service.apply(wrong,false,ADMIN).failed()).isEqualTo(1);assertThat(publications.count()).isZero();
        service.apply(request(preview()),false,ADMIN);var release=releaseFor(visitIds.getFirst());assertThatThrownBy(()->published.current(otherId,release.id(),ADMIN)).hasMessageContaining("không thuộc");
        assertThatThrownBy(()->writer.revoke(release.id(),new RevokeRequest(otherId,release.rowVersion(),"Sai người"),ADMIN)).hasMessageContaining("không thuộc");
    }
    @Test void publishedPdfUsesFrozenValuesAndClearlyIdentifiesTheRelease() throws Exception {
        released();var release=releaseFor(visitIds.getFirst());byte[] bytes=exports.export(employeeId,release.id(),ADMIN);
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(bytes)){String text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);assertThat(text).contains("PHIÊN BẢN CÔNG BỐ","Kết luận giả lập <0.5","Phiên bản 1").doesNotContain("Nội bộ, chưa công bố");}
        var folder=java.nio.file.Path.of("/tmp/health-phase6-fixtures");java.nio.file.Files.createDirectories(folder);java.nio.file.Files.setPosixFilePermissions(folder,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));var file=folder.resolve("published-fake.pdf");java.nio.file.Files.write(file,bytes);java.nio.file.Files.setPosixFilePermissions(file,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
    }
    @Test void importingASupplementResetsReviewButKeepsPublishedSnapshotUntilExplicitRepublish() throws Exception {
        var originalVisit=visits.findById(visitIds.getFirst()).orElseThrow();originalVisit.setExaminedOn(LocalDate.of(2025,9,1));visits.saveAndFlush(originalVisit);
        released();var release=releaseFor(visitIds.getFirst());
        var columns=Map.of(0,new com.booking.system.health.api.HrHealthImportDtos.Mapping(com.booking.system.health.api.HrHealthImportDtos.Field.NAME,null,null,null),1,new com.booking.system.health.api.HrHealthImportDtos.Mapping(com.booking.system.health.api.HrHealthImportDtos.Field.EMPLOYEE_CODE,null,null,null),2,new com.booking.system.health.api.HrHealthImportDtos.Mapping(com.booking.system.health.api.HrHealthImportDtos.Field.SID,null,null,null),3,new com.booking.system.health.api.HrHealthImportDtos.Mapping(com.booking.system.health.api.HrHealthImportDtos.Field.RESULT,indicator.getId(),"",""),4,new com.booking.system.health.api.HrHealthImportDtos.Mapping(com.booking.system.health.api.HrHealthImportDtos.Field.IGNORE,null,null,null));
        var mapping=new com.booking.system.health.api.HrHealthImportDtos.StageRequest(campaignId,"Kết quả",1,1,"PUBLIC_DEV",false,LocalDate.of(2025,9,1),columns,null);
        var file=HealthPhase2Test.book(new String[][]{{"Nhân viên công bố giả lập PUBLICDEV01","PUBLICDEV01","FAKE-PUB-1","Kết luận bổ sung giả lập",""}});
        var batch=imports.stage(file,mapping,ADMIN);var row=imports.preview(batch.id(),0,20,ADMIN).content().getFirst();assertThat(row.errors()).isEmpty();
        var decision=new com.booking.system.health.api.HrHealthImportDtos.RowDecision(row.id(),row.previewToken(),Map.of(row.cells().getFirst().key(),com.booking.system.health.api.HrHealthImportDtos.Choice.REPLACE),"Đính chính nguồn giả lập");
        importWriter.confirm(batch.id(),new com.booking.system.health.api.HrHealthImportDtos.ConfirmRequest(List.of(decision)),ADMIN);
        assertThat(visits.findById(visitIds.getFirst()).orElseThrow().getStatus()).isEqualTo("REVIEW");assertThat(publications.count()).isEqualTo(3);
        assertThat(published.current(employeeId,release.id(),ADMIN).snapshot().results().getFirst().raw()).isEqualTo("Kết luận giả lập <0.5");assertThat(preview().rows().getFirst().visits()).anyMatch(v->v.visitId().equals(visitIds.getFirst())&&!v.reviewed()&&v.state().equals("CHANGED"));
    }
    @Test void everyPublicationRouteRequiresManagersAndValidatesBodies() throws Exception {
        String body=com.booking.system.health.importer.HealthImportSupport.json(scope());
        for(String role:List.of("ADMIN","MANAGER"))mvc.perform(post("/api/v1/hr/health/publications/preview").header("Authorization","Bearer "+role).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        for(String path:List.of("preview","review","publish","revoke-batch")){mvc.perform(post("/api/v1/hr/health/publications/"+path).header("Authorization","Bearer HR_STAFF").contentType("application/json").content(body)).andExpect(status().isForbidden());mvc.perform(post("/api/v1/hr/health/publications/"+path).contentType("application/json").content(body)).andExpect(status().isUnauthorized());}
        mvc.perform(post("/api/v1/hr/health/publications/publish").header("Authorization","Bearer ADMIN").contentType("application/json").content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("status").value(400));
        mvc.perform(get("/api/v1/hr/health/publications/candidates").header("Authorization","Bearer ADMIN").param("campaignId",campaignId).param("size","21")).andExpect(status().isBadRequest());
    }
    @Test void concurrentAdminsNeverCreateDuplicateRevisions() throws Exception {
        var ready=request(reviewed());
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=executor.submit(()->service.apply(ready,false,ADMIN));var second=executor.submit(()->service.apply(ready,false,new HrImportActor("USER:other-fake","Other fake","MANAGER")));
            first.get(20,java.util.concurrent.TimeUnit.SECONDS);second.get(20,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(publications.count()).isEqualTo(3);assertThat(publications.findAll()).allMatch(p->p.getRevision()==1);
        assertThat(jdbc.queryForObject("select count(*) from hr_health_audit_events where action='PUBLISH_VISIT'",Integer.class)).isEqualTo(3);
    }
    @Test void revokingNewestReleaseDoesNotExposeOlderPublishedRowsOrPermitStaleRepublish() {
        released();var old=releaseFor(visitIds.getFirst());var result=results.findByVisitIdIn(List.of(visitIds.getFirst())).getFirst();result.setRawValue("Phiên bản chỉnh sửa giả lập");results.saveAndFlush(result);
        service.apply(request(preview()),true,ADMIN);service.apply(request(preview()),false,ADMIN);var latest=releaseFor(visitIds.getFirst());assertThat(latest.revision()).isEqualTo(2);
        var stale=request(preview());writer.revoke(latest.id(),new RevokeRequest(employeeId,latest.rowVersion(),"Thu hồi bản mới"),ADMIN);
        assertThatThrownBy(()->published.current(employeeId,old.id(),ADMIN)).hasMessageContaining("không còn");assertThatThrownBy(()->published.current(employeeId,latest.id(),ADMIN)).hasMessageContaining("không còn");
        assertThat(service.apply(stale,false,ADMIN).failed()).isEqualTo(1);assertThat(releaseFor(visitIds.getFirst()).status()).isEqualTo("REVOKED");
    }
    @Test void catalogEditsAndInvalidSourceClassificationCannotBypassReview() {
        var ready=reviewed();indicator.setName("Tên chỉ tiêu chỉnh sửa giả lập");indicators.saveAndFlush(indicator);assertThat(service.apply(request(ready),false,ADMIN).failed()).isEqualTo(2);assertThat(publications.count()).isZero();
        var classification=new HealthIndicator();classification.setCode("HEALTH_CLASSIFICATION");classification.setName("Phân loại sức khỏe");classification.setCategory("Kết luận");classification.setValueType(HealthTypes.IndicatorType.CODED);classification.setStatus(HealthTypes.IndicatorStatus.ACTIVE);HealthAccess.audit(classification,ADMIN,true);classification=indicators.saveAndFlush(classification);
        var value=new HealthResult();value.setVisitId(visitIds.getFirst());value.setIndicatorId(classification.getId());value.setValueKey("fake-classification");value.setRawValue("VI");HealthAccess.audit(value,ADMIN,true);results.saveAndFlush(value);
        assertThat(service.apply(request(preview()),true,ADMIN).failed()).isEqualTo(1);assertThat(preview().rows().getFirst().visits()).anyMatch(v->v.visitId().equals(visitIds.getFirst())&&v.blockers().stream().anyMatch(b->b.contains("I–V")));
    }
    @Test void publishedDownloadsAndContentAreAuthorizedOnEveryRequest() throws Exception {
        released();var release=releaseFor(visitIds.getFirst());String path="/api/v1/hr/health/publications/"+release.id();
        for(String suffix:List.of("/results","/pdf")) {
            mvc.perform(get(path+suffix).param("employeeId",employeeId)).andExpect(status().isUnauthorized());
            mvc.perform(get(path+suffix).header("Authorization","Bearer HR_STAFF").param("employeeId",employeeId)).andExpect(status().isForbidden());
            mvc.perform(get(path+suffix).header("Authorization","Bearer MANAGER").param("employeeId",employeeId)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
            mvc.perform(get(path+suffix).header("Authorization","Bearer ADMIN").param("employeeId",otherId)).andExpect(status().isNotFound());
        }
        writer.revoke(release.id(),new RevokeRequest(employeeId,release.rowVersion(),"Thu hồi để kiểm tra API"),ADMIN);
        for(String suffix:List.of("/results","/pdf"))mvc.perform(get(path+suffix).header("Authorization","Bearer ADMIN").param("employeeId",employeeId)).andExpect(status().isNotFound());
    }
    @jakarta.annotation.Resource HealthSelfRecordService selfRecords;
    @Test void personalHistoryUsesPublishedSnapshotsAndNeverShowsAnotherPersonsVisit() {
        released();
        var profile=selfRecords.profile(employeeId,2025,2026,0,20);
        assertThat(profile.releases().totalElements()).isEqualTo(2);
        var release=releaseFor(visitIds.getFirst());
        var detail=selfRecords.detail(employeeId,release.id(),null,0,20);
        assertThat(detail.results().content().getFirst().sourceSheet()).isNull();
        assertThat(detail.results().content().getFirst().sourceRow()).isNull();
        var wrong=releaseFor(visitIds.get(2));
        assertThatThrownBy(()->selfRecords.detail(employeeId,wrong.id(),null,0,20)).hasMessageContaining("không còn");
        assertThatThrownBy(()->selfRecords.pdf(employeeId,wrong.id())).hasMessageContaining("không còn");
        writer.revoke(release.id(),new RevokeRequest(employeeId,release.rowVersion(),"Thu hồi giả lập"),ADMIN);
        assertThat(selfRecords.profile(employeeId,2025,2026,0,20).releases().totalElements()).isEqualTo(1);
        assertThatThrownBy(()->selfRecords.detail(employeeId,release.id(),null,0,20)).hasMessageContaining("không còn");
    }
    @Test void personalComparisonCannotUseUnpublishedOrForeignReleasesAndKeepsFrozenResults() {
        released();var a=releaseFor(visitIds.get(0));var b=releaseFor(visitIds.get(1));var foreign=releaseFor(visitIds.get(2));
        var edited=results.findByVisitIdIn(List.of(visitIds.getFirst())).getFirst();edited.setRawValue("Bản biên tập chưa công bố");results.saveAndFlush(edited);
        var compare=selfRecords.compare(employeeId,List.of(a.id(),b.id()),null,0,20);
        assertThat(compare.rows().content()).hasSize(1);
        assertThat(compare.rows().content().getFirst().cells()).allSatisfy(c->{assertThat(c.raw()).isEqualTo("Kết luận giả lập <0.5");assertThat(c.sourceSheet()).isNull();});
        assertThatThrownBy(()->selfRecords.compare(employeeId,List.of(a.id(),foreign.id()),null,0,20)).hasMessageContaining("không còn");
        assertThatThrownBy(()->selfRecords.compare(employeeId,List.of(a.id(),a.id()),null,0,20)).hasMessageContaining("khác nhau");
        assertThatThrownBy(()->selfRecords.profile(employeeId,2020,2030,0,20)).hasMessageContaining("10 năm");
        assertThatThrownBy(()->selfRecords.detail(employeeId,a.id(),null,0,200)).hasMessageContaining("Phân trang");
    }

}
