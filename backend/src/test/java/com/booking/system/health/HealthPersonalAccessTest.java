package com.booking.system.health;

import com.booking.system.health.api.HealthPersonalAccessDtos.*;
import com.booking.system.health.api.HrHealthPublicationDtos.RevokeRequest;
import com.booking.system.health.service.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.booking.system.health.importer.HealthImportSupport.*;

@SpringBootTest(classes=HealthPhase2Test.Config.class,properties={
 "spring.datasource.url=jdbc:h2:mem:health_self;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false","spring.flyway.enabled=true","cfc.health.enabled=true",
 "cfc.health.self-service.enabled=true","logging.level.org.springframework.web=INFO","logging.level.org.hibernate.SQL=OFF"})
@AutoConfigureMockMvc
class HealthPersonalAccessTest extends HealthPublicationTest {
    @jakarta.annotation.Resource HealthPersonalAccessService personal;
    @jakarta.annotation.Resource HealthLookupLimiter limiter;
    static final String CITIZEN="000000000001";
    @BeforeEach void fakeIdentity() {
        jdbc.update("INSERT INTO hr_employee_identity(employee_id,citizen_identity_number,verification_status,created_by_actor,updated_by_actor,row_version) VALUES(?,?,'UNVERIFIED',?,?,0)",employeeId,CITIZEN,ADMIN.subject(),ADMIN.subject());
        ((Map<?,?>)ReflectionTestUtils.getField(limiter,"attempts")).clear();
    }
    @AfterEach void cleanupIdentity(){jdbc.update("DELETE FROM hr_employee_identity WHERE employee_id=?",employeeId);}
    Change change(){return new Change(personal.status(employeeId,ADMIN).generation(),"Xác minh và giao mã giả lập");}
    Issued issue(){return personal.issue(employeeId,change(),ADMIN);}
    Session session(Issued issued){return personal.lookup(new Lookup(CITIZEN,"PUBLICDEV01",issued.code()),"127.0.0.1");}

    @Test void issuesOnlyHashesAndRotatingCodeInvalidatesAllOldSessions() {
        var issued=issue();assertThat(issued.code()).hasSize(32);
        assertThat(jdbc.queryForObject("SELECT secret_hash FROM hr_health_access_grants WHERE employee_id=?",String.class,employeeId)).isEqualTo(hash(issued.code())).isNotEqualTo(issued.code());
        var session=session(issued);assertThat(personal.owner(session.token())).isEqualTo(employeeId);
        assertThat(jdbc.queryForObject("SELECT token_hash FROM hr_health_access_sessions WHERE employee_id=?",String.class,employeeId)).isEqualTo(hash(session.token()));
        issue();assertThatThrownBy(()->personal.owner(session.token())).hasMessageContaining("Không thể xác minh");
        assertThatThrownBy(()->session(issued)).hasMessageContaining("Không thể xác minh");
    }
    @Test void wrongCitizenWrongCodeUnknownPersonAndExpiredGrantHaveSameMessage() {
        var issued=issue();String expected="Không thể xác minh thông tin hoặc phiên đã hết hạn. Vui lòng kiểm tra lại hoặc liên hệ nhân sự.";
        for(var request:List.of(new Lookup("000000000002","PUBLICDEV01",null,LookupMode.IDENTITY),new Lookup(CITIZEN,"PUBLICDEV01","X".repeat(32)),new Lookup(CITIZEN,"MISSING",null,LookupMode.IDENTITY)))assertThatThrownBy(()->personal.lookup(request,"127.0.0.2")).hasMessage(expected);
        jdbc.update("UPDATE hr_health_access_grants SET expires_at=DATEADD('DAY',-1,CURRENT_TIMESTAMP)");
        assertThatThrownBy(()->session(issued)).hasMessage(expected);
    }
    @Test void revokedCodesAndExpiredSessionsAndLogoutAreRejected() {
        var issued=issue();var session=session(issued);personal.logout(session.token());
        String loggedOut=session.token();assertThatThrownBy(()->personal.owner(loggedOut)).hasMessageContaining("Không thể xác minh");
        session=session(issued);jdbc.update("UPDATE hr_health_access_sessions SET expires_at=?",java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(1));
        String expired=session.token();assertThatThrownBy(()->personal.owner(expired)).hasMessageContaining("Không thể xác minh");
        session=session(issued);String active=session.token();personal.revoke(employeeId,change(),ADMIN);
        assertThatThrownBy(()->personal.owner(active)).hasMessageContaining("Không thể xác minh");assertThatThrownBy(()->session(issued)).hasMessageContaining("Không thể xác minh");
    }
    @Test void concurrentAdminIntentCannotOverwriteNewCodeAndMissingIdentityCannotBeIssued() {
        var stale=change();issue();assertThatThrownBy(()->personal.issue(employeeId,stale,ADMIN)).hasMessageContaining("thay đổi");
        assertThatThrownBy(()->personal.issue(otherId,new Change(null,"Fixture"),ADMIN)).hasMessageContaining("CCCD");
        assertThatThrownBy(()->personal.issue(employeeId,change(),new com.booking.system.hr.importer.HrImportActor("staff","Fixture","HR_STAFF"))).hasMessageContaining("Chỉ quản trị");
    }
    @Test void personalApiCannotReadAnotherPersonOrUnpublishedDataOrBypassWithAdminJwt() throws Exception {
        released();var session=session(issue());String own=releaseFor(visitIds.getFirst()).id(),foreign=releaseFor(visitIds.get(2)).id();
        mvc.perform(get("/api/v1/health-self/profile").param("from","2025").param("to","2026").header("X-Health-Session",session.token())).andExpect(status().isOk()).andExpect(jsonPath("$.data.releases.totalElements").value(2)).andExpect(header().string("Cache-Control","no-store, private"));
        mvc.perform(get("/api/v1/health-self/releases/"+own).header("X-Health-Session",session.token())).andExpect(status().isOk()).andExpect(jsonPath("$.data.results.content[0].sourceRow").isEmpty()).andExpect(jsonPath("$.data.publication.createdByActor").doesNotExist());
        mvc.perform(get("/api/v1/health-self/releases/"+foreign).header("X-Health-Session",session.token())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/health-self/releases/"+foreign+"/pdf").header("X-Health-Session",session.token())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/health-self/releases/"+own).header("Authorization","Bearer ADMIN")).andExpect(status().isUnauthorized());
        var published=releaseFor(visitIds.getFirst());writer.revoke(own,new RevokeRequest(employeeId,published.rowVersion(),"Thu hồi fixture"),ADMIN);
        mvc.perform(get("/api/v1/health-self/releases/"+own).header("X-Health-Session",session.token())).andExpect(status().isNotFound());
    }
    @Test void publicLookupAndAllFailureResponsesHavePrivacyHeadersAndNoCookie() throws Exception {
        var issued=issue();
        mvc.perform(post("/api/v1/health-self/session").contentType("application/json").content(json(new Lookup(CITIZEN,"PUBLICDEV01",issued.code())))).andExpect(status().isOk()).andExpect(jsonPath("$.data.token").isString()).andExpect(header().doesNotExist("Set-Cookie")).andExpect(header().string("Referrer-Policy","no-referrer"));
        mvc.perform(post("/api/v1/health-self/session").contentType("application/json").content("{}" )).andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control","no-store, private"));
        mvc.perform(get("/api/v1/health-self/profile").param("from","2025").param("to","2026")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","no-store, private"));
    }
    @Test void managementEndpointsRejectGuestsStaffAndUnvalidatedBodies() throws Exception {
        String path="/api/v1/hr/health/personal-access/"+employeeId;
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization","Bearer HR_STAFF")).andExpect(status().isForbidden());
        mvc.perform(post(path+"/issue").header("Authorization","Bearer MANAGER").contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(post(path+"/issue").header("Authorization","Bearer MANAGER").contentType("application/json").content(json(change()))).andExpect(status().isOk()).andExpect(jsonPath("$.data.code").isString());
    }
    @Test void lookupRateLimitIsBoundedAndDeclaredExpiryIndexIsUsed() {
        issue();var request=new Lookup(CITIZEN,"PUBLICDEV01","X".repeat(32));
        for(int i=0;i<10;i++)assertThatThrownBy(()->personal.lookup(request,"127.0.0.3")).hasMessageContaining("Không thể xác minh");
        assertThatThrownBy(()->personal.lookup(request,"127.0.0.4")).hasMessageContaining("15 phút");
        String plan=jdbc.queryForObject("EXPLAIN DELETE FROM hr_health_access_sessions WHERE expires_at<CURRENT_TIMESTAMP",String.class);
        assertThat(plan).containsIgnoringCase("idx_health_access_session_expiry");System.out.println("HEALTH_SELF_H2_EXPLAIN="+plan.replace('\n',' '));
    }
    @Test void personalPdfContainsPublishedResultsButNoInternalReviewReason() throws Exception {
        released();var session=session(issue());String id=releaseFor(visitIds.getFirst()).id();
        byte[] bytes=mvc.perform(get("/api/v1/health-self/releases/"+id+"/pdf").header("X-Health-Session",session.token())).andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andReturn().getResponse().getContentAsByteArray();
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(bytes)){
            String text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);assertThat(text).contains("CÁ NHÂN").contains("Kết luận giả lập").doesNotContain("Đối chiếu nguồn giả lập").doesNotContain("Sheet giả lập");
        }
    }
    @Test void personalHistoryYearDoesNotChangeWhenTheEditingCampaignYearChanges() {
        released();var campaign=campaigns.findById(campaignId).orElseThrow();campaign.setReportYear(2026);campaigns.saveAndFlush(campaign);
        assertThat(selfRecords.profile(employeeId,2025,2025,0,20).releases().content()).hasSize(2).allSatisfy(v->assertThat(v.year()).isEqualTo(2025));
        assertThat(selfRecords.profile(employeeId,2026,2026,0,20).releases().content()).isEmpty();
    }

    @Test void identityOnlyRequiresExplicitManagerPermissionAndBothModesThenWork() {
        var issued=issue();var basic=new Lookup(CITIZEN,"PUBLICDEV01",null,LookupMode.IDENTITY);
        assertThatThrownBy(()->personal.lookup(basic,"127.0.0.7")).hasMessageContaining("Không thể xác minh");
        var status=personal.status(employeeId,ADMIN);
        personal.policy(employeeId,new LookupPolicy(status.generation(),"Cho phép tra cứu hai trường giả lập",true),ADMIN);
        assertThat(personal.status(employeeId,ADMIN).allowIdentityOnly()).isTrue();
        var basicSession=personal.lookup(basic,"127.0.0.7");assertThat(personal.owner(basicSession.token())).isEqualTo(employeeId);
        var codedSession=session(issued);assertThat(personal.owner(codedSession.token())).isEqualTo(employeeId);
        assertThatThrownBy(()->personal.lookup(new Lookup("000000000002","PUBLICDEV01",null,LookupMode.IDENTITY),"127.0.0.7")).hasMessageContaining("Không thể xác minh");
        assertThatThrownBy(()->personal.lookup(new Lookup(CITIZEN,"PUBLICDEV01",null,null),"127.0.0.7")).hasMessageContaining("Không thể xác minh");
        assertThatThrownBy(()->personal.lookup(new Lookup(CITIZEN,"PUBLICDEV01","X".repeat(32),LookupMode.ACCESS_CODE),"127.0.0.7")).hasMessageContaining("Không thể xác minh");
    }
    @Test void restrictingOrReissuingPermissionTerminatesBasicSessionsAndCannotUseStaleGeneration() {
        issue();var original=personal.status(employeeId,ADMIN);
        personal.policy(employeeId,new LookupPolicy(original.generation(),"Bật cách hai trường giả lập",true),ADMIN);
        var basic=new Lookup(CITIZEN,"PUBLICDEV01",null,LookupMode.IDENTITY);var token=personal.lookup(basic,"127.0.0.8").token();
        assertThatThrownBy(()->personal.policy(employeeId,new LookupPolicy(original.generation(),"Lựa chọn cũ",true),ADMIN)).hasMessageContaining("thay đổi");
        personal.policy(employeeId,new LookupPolicy(personal.status(employeeId,ADMIN).generation(),"Chỉ dùng mã riêng giả lập",false),ADMIN);
        assertThatThrownBy(()->personal.owner(token)).hasMessageContaining("Không thể xác minh");
        assertThatThrownBy(()->personal.lookup(basic,"127.0.0.8")).hasMessageContaining("Không thể xác minh");
        personal.policy(employeeId,new LookupPolicy(personal.status(employeeId,ADMIN).generation(),"Bật lại giả lập",true),ADMIN);
        String previous=personal.lookup(basic,"127.0.0.8").token();issue();
        assertThat(personal.status(employeeId,ADMIN).allowIdentityOnly()).isFalse();
        assertThatThrownBy(()->personal.owner(previous)).hasMessageContaining("Không thể xác minh");
        assertThatThrownBy(()->personal.lookup(basic,"127.0.0.8")).hasMessageContaining("Không thể xác minh");
    }
    @Test void policyAndTwoModeApiValidateBodiesAndRetainPrivateResponses() throws Exception {
        issue();var policy=new LookupPolicy(personal.status(employeeId,ADMIN).generation(),"Bật hai cách tra cứu giả lập",true);
        String path="/api/v1/hr/health/personal-access/"+employeeId+"/policy";
        mvc.perform(post(path).header("Authorization","Bearer HR_STAFF").contentType("application/json").content(json(policy))).andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization","Bearer ADMIN").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post(path).header("Authorization","Bearer ADMIN").contentType("application/json").content(json(policy))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(post("/api/v1/health-self/session").contentType("application/json").content(json(new Lookup(CITIZEN,"PUBLICDEV01",null,LookupMode.IDENTITY)))).andExpect(status().isOk()).andExpect(jsonPath("$.data.token").isString());
        mvc.perform(post("/api/v1/health-self/session").contentType("application/json").content(json(new Lookup(CITIZEN,"PUBLICDEV01",null,LookupMode.ACCESS_CODE)))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/health-self/session").contentType("application/json").content("{\"citizenNumber\":\"000000000001\",\"employeeCode\":\"PUBLICDEV01\",\"mode\":\"UNKNOWN\"}")).andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control","no-store, private"));
    }

    @Test void privateCodeAloneResolvesItsOwnerWithoutIdentityAndIgnoresForgedEmployeeChoice() throws Exception {
        var issued=issue();
        var privateSession=personal.lookup(new Lookup(null,null,issued.code(),LookupMode.ACCESS_CODE),"127.0.0.9");
        assertThat(personal.owner(privateSession.token())).isEqualTo(employeeId);
        var forged=personal.lookup(new Lookup("000000000002","PUBLICDEV02",issued.code(),LookupMode.ACCESS_CODE),"127.0.0.9");
        assertThat(personal.owner(forged.token())).isEqualTo(employeeId);
        jdbc.update("DELETE FROM hr_employee_identity WHERE employee_id=?",employeeId);
        mvc.perform(post("/api/v1/health-self/session").contentType("application/json").content(json(new Lookup(null,null,issued.code(),LookupMode.ACCESS_CODE))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.token").isString()).andExpect(header().string("Cache-Control","no-store, private"));
        String plan=jdbc.queryForObject("EXPLAIN SELECT employee_id FROM hr_health_access_grants WHERE secret_hash=?",String.class,"a".repeat(64));
        assertThat(plan).containsIgnoringCase("uk_health_access_secret_hash");System.out.println("HEALTH_PRIVATE_LOOKUP_H2_EXPLAIN="+plan.replace('\n',' '));
    }
    @Test void conditionalLookupValidationRequiresOnlyFieldsForTheSelectedMethod() throws Exception {
        for(var request:List.of(new Lookup(null,"PUBLICDEV01",null,LookupMode.IDENTITY),new Lookup(CITIZEN,null,null,LookupMode.IDENTITY),new Lookup(null,null,null,LookupMode.ACCESS_CODE)))
            mvc.perform(post("/api/v1/health-self/session").contentType("application/json").content(json(request))).andExpect(status().isBadRequest());
        assertThatThrownBy(()->personal.lookup(new Lookup(null,null,"X".repeat(32),LookupMode.ACCESS_CODE),"127.0.0.10")).hasMessageContaining("Không thể xác minh");
    }

}
