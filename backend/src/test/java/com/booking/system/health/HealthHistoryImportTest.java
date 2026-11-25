package com.booking.system.health;

import com.booking.system.entity.User;
import com.booking.system.enums.*;
import com.booking.system.health.importer.*;
import com.booking.system.health.importer.baseline.*;
import com.booking.system.health.importer.history.*;
import com.booking.system.health.repository.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.booking.system.health.importer.baseline.HealthBaselineData.*;

@SpringBootTest(classes={HealthPhase2Test.Config.class,HealthHistoryImportTest.FixtureConfig.class},properties={
 "spring.datasource.url=jdbc:h2:mem:health_history_import;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false","spring.flyway.enabled=true","cfc.health.enabled=true",
 "logging.level.org.springframework.web=INFO","logging.level.org.hibernate.SQL=OFF"})
@AutoConfigureMockMvc
class HealthHistoryImportTest {
    @Autowired ApplicationContext context;
    @Autowired HealthHistoryImport history;
    @Autowired HealthHistoryStore store;
    @Autowired @Qualifier("historyTestDirectory") Path directory;
    @Autowired MockMvc mvc;
    HealthBaselineIntegrationTest fixture;
    static final byte[] KEY=new byte[32];
    @TestConfiguration(proxyBeanMethods=false)
    @Import(com.booking.system.health.config.HealthHistoryStartup.class)
    static class FixtureConfig {
        @Bean("historyTestDirectory") Path directory() throws Exception{return Files.createTempDirectory("cfc-health-history-test-");}
        @Bean @Primary HealthHistoryStore testHistoryStore(@Qualifier("historyTestDirectory") Path path){return new HealthHistoryStore(new FileSystemResource(path.resolve("data.bundle")),path.resolve("private/history.key"));}
    }
    @BeforeEach void setup() throws Exception {
        fixture=new HealthBaselineIntegrationTest();context.getAutowireCapableBeanFactory().autowireBean(fixture);fixture.clearAndSeed();
        var employee=fixture.people.findAll().stream().filter(e->e.getEmployeeCode().equals("BASEDEV01")).findFirst().orElseThrow();employee.setDateOfBirth(LocalDate.of(1990,1,2));fixture.people.saveAndFlush(employee);
        Files.deleteIfExists(directory.resolve("private/history.key"));Files.deleteIfExists(directory.resolve("private/history-import-receipt.json"));
        bundle(fixture.dataset("II"));
        var tokens=context.getBean(com.booking.system.security.JwtUtils.class);var users=context.getBean(com.booking.system.repository.UserRepository.class);
        for(var role:List.of(RoleEnum.ADMIN,RoleEnum.MANAGER,RoleEnum.HR_STAFF)) {
            var user=new User();user.setId("history-user-"+role);user.setEmail(role+"@example.test");user.setFullName("Fixture "+role);user.setRole(role);user.setStatus(UserStatus.ACTIVE);
            when(tokens.validateJwtToken(role.name())).thenReturn(true);when(tokens.getEmailFromJwtToken(role.name())).thenReturn(user.getEmail());when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        }
    }
    void bundle(Dataset data) throws Exception {Files.write(directory.resolve("data.bundle"),HealthHistoryCrypto.seal(new HealthHistoryCrypto.Package(1,true,data),KEY));}
    void execute(){store.install(KEY);history.execute(HealthBaselineIntegrationTest.ADMIN,true,"Nguồn giả lập đã duyệt.");}
    @Test void deployWithoutThePrivateKeyDoesNotWriteAnyHistoryData() {
        assertThat(history.status(HealthBaselineIntegrationTest.ADMIN).state()).isEqualTo("LOCKED");
        assertThat(history.start(HealthBaselineIntegrationTest.ADMIN,false,"Khởi động giả lập.").state()).isEqualTo("LOCKED");
        assertThat(fixture.results.count()).isZero();assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM hr_health_imports",Integer.class)).isZero();
    }
    @Test void oneTimeKeyUploadImportsMatchingRowsAndNeverPublishes() throws Exception {
        mvc.perform(multipart("/api/v1/hr/health/history-import/key").file(new MockMultipartFile("file","history.key","application/octet-stream",KEY.clone())).header("Authorization","Bearer ADMIN"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        for(int n=0;n<200&&history.status(HealthBaselineIntegrationTest.ADMIN).state().equals("RUNNING");n++)Thread.sleep(20);
        var state=history.status(HealthBaselineIntegrationTest.ADMIN);assertThat(state.state()).isEqualTo("DONE");assertThat(state.imported()).isEqualTo(1);assertThat(state.pending()).isZero();
        assertThat(fixture.results.count()).isEqualTo(2);assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM hr_health_publications",Integer.class)).isZero();
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM hr_health_campaigns WHERE report_year=2026",Integer.class)).isZero();
    }
    @Test void applicationReadyAutomaticallyImportsOnceWhenTheServerAlreadyHasItsKey() throws Exception {
        store.install(KEY);
        context.publishEvent(new org.springframework.boot.context.event.ApplicationReadyEvent(new org.springframework.boot.SpringApplication(HealthPhase2Test.Config.class),new String[0],(org.springframework.context.ConfigurableApplicationContext)context,java.time.Duration.ZERO));
        for(int n=0;n<200&&history.status(HealthBaselineIntegrationTest.ADMIN).state().equals("RUNNING");n++)Thread.sleep(20);
        assertThat(history.status(HealthBaselineIntegrationTest.ADMIN).state()).isEqualTo("DONE");
        assertThat(fixture.results.count()).isEqualTo(2);
        long count=fixture.jdbc.queryForObject("SELECT COUNT(*) FROM hr_health_imports",Long.class);
        context.publishEvent(new org.springframework.boot.context.event.ApplicationReadyEvent(new org.springframework.boot.SpringApplication(HealthPhase2Test.Config.class),new String[0],(org.springframework.context.ConfigurableApplicationContext)context,java.time.Duration.ZERO));
        for(int n=0;n<200&&history.status(HealthBaselineIntegrationTest.ADMIN).state().equals("RUNNING");n++)Thread.sleep(20);
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM hr_health_imports",Long.class)).isEqualTo(count);
    }
    @Test void redeployUsesReceiptAndExplicitRetryDoesNotCreateDuplicateRows() {
        execute();long audits=fixture.jdbc.queryForObject("SELECT COUNT(*) FROM hr_health_audit_events",Long.class);
        history.execute(HealthBaselineIntegrationTest.ADMIN,false,"Khởi động lại.");
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM hr_health_audit_events",Long.class)).isEqualTo(audits);
        history.execute(HealthBaselineIntegrationTest.ADMIN,true,"Thử lại có kiểm tra.");
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM hr_health_imports",Integer.class)).isEqualTo(1);
        assertThat(fixture.results.count()).isEqualTo(2);assertThat(history.status(HealthBaselineIntegrationTest.ADMIN).imported()).isEqualTo(1);
    }
    @Test void nameOnlyAndUnknownBirthRemainPendingUntilVerified() throws Exception {
        var missing=fixture.reader.read(List.of(HealthBaselineAnalysisTest.fixture("Tổng hợp","010123-BASE","Nhân viên baseline giả lập","","20","II")));
        bundle(missing);execute();assertThat(history.status(HealthBaselineIntegrationTest.ADMIN).pending()).isEqualTo(1);assertThat(fixture.results.count()).isZero();
    }
    @Test void employeeWithoutBirthCanBeImportedOnRetryAfterPersonnelWasVerified() {
        var employee=fixture.people.findAll().stream().filter(e->e.getEmployeeCode().equals("BASEDEV01")).findFirst().orElseThrow();employee.setDateOfBirth(null);employee=fixture.people.saveAndFlush(employee);
        execute();assertThat(fixture.results.count()).isZero();
        employee.setDateOfBirth(LocalDate.of(1990,1,2));fixture.people.saveAndFlush(employee);
        history.execute(HealthBaselineIntegrationTest.ADMIN,true,"Đã xác minh nhân sự.");
        assertThat(fixture.results.count()).isEqualTo(2);assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM hr_health_imports",Integer.class)).isEqualTo(1);
    }
    @Test void anUpdatedBundleNeverOverwritesAnExistingConflictingResult() throws Exception {
        execute();var replacement=fixture.reader.read(List.of(HealthBaselineAnalysisTest.fixture("Tổng hợp","010123-BASE","Nhân viên baseline giả lập","BASEDEV01","99","II")));
        bundle(replacement);history.execute(HealthBaselineIntegrationTest.ADMIN,false,"Gói mới giả lập.");
        assertThat(fixture.results.findAll()).extracting(r->r.getRawValue()).contains("21").doesNotContain("99");
        assertThat(history.status(HealthBaselineIntegrationTest.ADMIN).pending()).isEqualTo(1);
    }
    @Test void aSidAlreadyImportedFromAnotherSourceIsHeldInsteadOfDuplicated() throws Exception {
        var prepared=fixture.preparation.prepare(fixture.dataset("II"),fixture.analyzer.analyze(fixture.dataset("II"),fixture.preparation.identities()),"OTHER_SOURCE",Policy.TOTAL_FIRST,true,HealthBaselineIntegrationTest.ADMIN);
        var batch=prepared.batches().getFirst();var row=fixture.imports.preview(batch.id(),0,20,HealthBaselineIntegrationTest.ADMIN).content().getFirst();
        fixture.writer.confirm(batch.id(),new com.booking.system.health.api.HrHealthImportDtos.ConfirmRequest(List.of(new com.booking.system.health.api.HrHealthImportDtos.RowDecision(row.id(),row.previewToken(),Map.of(),null))),HealthBaselineIntegrationTest.ADMIN);
        // Use a differently serialized source so this package has its own staging hash.
        var addition=fixture.reader.read(List.of(HealthBaselineAnalysisTest.fixture("Tổng hợp","010123-BASE","Nhân viên baseline giả lập","BASEDEV01","22","II")));
        bundle(addition);execute();
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM hr_health_visits",Integer.class)).isEqualTo(1);
        assertThat(history.status(HealthBaselineIntegrationTest.ADMIN).pending()).isEqualTo(1);
        assertThat(fixture.results.findAll()).extracting(r->r.getRawValue()).doesNotContain("22");
    }
    @Test void aManuallyStagedBatchWithAnotherNamespaceIsNotAutomaticallyConfirmed() throws Exception {
        var data=fixture.dataset("II");bundle(data);fixture.preparation.prepare(data,fixture.analyzer.analyze(data,fixture.preparation.identities()),"OTHER_SOURCE",Policy.TOTAL_FIRST,true,HealthBaselineIntegrationTest.ADMIN);
        store.install(KEY);
        assertThatThrownBy(()->history.execute(HealthBaselineIntegrationTest.ADMIN,true,"Không đổi nguồn của lô thủ công.")).hasMessageContaining("mã nguồn khác");
        assertThat(fixture.results.count()).isZero();
    }
    @Test void anExistingManualMappingIsNotAutomaticallyConfirmedEvenWithTheSameNamespace() throws Exception {
        var data=fixture.dataset("II");bundle(data);
        var prepared=fixture.preparation.prepare(data,fixture.analyzer.analyze(data,fixture.preparation.identities()),"CFC_HISTORY",Policy.TOTAL_FIRST,true,HealthBaselineIntegrationTest.ADMIN);
        var batchId=prepared.batches().getFirst().id();
        String saved=fixture.jdbc.queryForObject("SELECT mapping_json FROM hr_health_imports WHERE id=?",String.class,batchId);
        var original=HealthImportSupport.read(saved,com.booking.system.health.api.HrHealthImportDtos.StageRequest.class);
        var changed=new com.booking.system.health.api.HrHealthImportDtos.StageRequest(original.campaignId(),original.sheet(),original.headerRow(),original.headerRows(),original.sourceNamespace(),true,null,original.columns(),null);
        fixture.jdbc.update("UPDATE hr_health_imports SET mapping_json=? WHERE id=?",HealthImportSupport.json(changed),batchId);
        store.install(KEY);
        assertThatThrownBy(()->history.execute(HealthBaselineIntegrationTest.ADMIN,true,"Giữ ánh xạ thủ công.")).hasMessageContaining("ánh xạ khác");
        assertThat(fixture.results.count()).isZero();
    }
    @Test void missingBatchesAfterDatabaseRestoreAreRecreatedDespiteAnExistingReceipt() {
        execute();fixture.clearAndSeed();var employee=fixture.people.findAll().stream().filter(e->e.getEmployeeCode().equals("BASEDEV01")).findFirst().orElseThrow();employee.setDateOfBirth(LocalDate.of(1990,1,2));fixture.people.saveAndFlush(employee);
        history.execute(HealthBaselineIntegrationTest.ADMIN,false,"Khôi phục DB giả lập cũ.");
        assertThat(fixture.results.count()).isEqualTo(2);
    }
    @Test void guestsAndHrStaffCannotReadStatusInstallKeysOrTriggerImports() throws Exception {
        String root="/api/v1/hr/health/history-import";
        mvc.perform(get(root)).andExpect(status().isUnauthorized());
        mvc.perform(get(root).header("Authorization","Bearer HR_STAFF")).andExpect(status().isForbidden());
        mvc.perform(multipart(root+"/key").file(new MockMultipartFile("file","key","application/octet-stream",KEY)).header("Authorization","Bearer HR_STAFF")).andExpect(status().isForbidden());
        mvc.perform(post(root+"/run").contentType("application/json").content("{\"reason\":\"test\"}").header("Authorization","Bearer HR_STAFF")).andExpect(status().isForbidden());
        mvc.perform(post(root+"/run").contentType("application/json").content("{\"reason\":\"\"}").header("Authorization","Bearer ADMIN")).andExpect(status().isBadRequest());
        mvc.perform(multipart(root+"/key").file(new MockMultipartFile("file","key","application/octet-stream",new byte[1])).header("Authorization","Bearer ADMIN")).andExpect(status().isBadRequest());
        assertThat(fixture.results.count()).isZero();
    }
}
