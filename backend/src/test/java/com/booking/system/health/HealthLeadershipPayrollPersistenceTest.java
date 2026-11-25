package com.booking.system.health;

import com.booking.system.hr.service.*;
import com.booking.system.hr.enums.HrPayrollType;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.HrLeadershipPayrollParserTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes=HealthPhase2Test.Config.class,properties={"spring.datasource.url=jdbc:h2:mem:leadership_persistence;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false","spring.flyway.enabled=true","cfc.health.enabled=true"})
@Import({HrPayrollUploadService.class,HrLeadershipPayrollParser.class,HrPayrollWorkbookParser.class,HrPayrollService.class})
class HealthLeadershipPayrollPersistenceTest {
    @Autowired HrPayrollUploadService upload;
    @Autowired HrPayrollService persistence;
    @Autowired JdbcTemplate jdbc;
    @Test void persistsTypeFiveRowsAndKeepsInvalidRowsBlockedAfterRefresh() throws Exception {
        var actor=new HrImportActor("QA","Fixture","ADMIN");
        var book=HrLeadershipPayrollParserTest.workbook(5);book.getSheetAt(0).getRow(2).getCell(3).setCellErrorValue(org.apache.poi.ss.usermodel.FormulaError.REF.getCode());
        var bytes=HrLeadershipPayrollParserTest.bytes(book);var batch=upload.upload("synthetic-leadership.xlsx",bytes,HrPayrollType.LEADERSHIP,actor);
        assertThat(batch.totalRows()).isEqualTo(5);assertThat(batch.invalidRows()).isEqualTo(1);assertThat(batch.skippedRows()).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT payroll_type FROM hr_payroll_imports WHERE id=?",String.class,batch.id())).isEqualTo("LEADERSHIP");
        persistence.refreshEligibility(batch.id(),actor);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hr_payroll_import_rows WHERE import_id=? AND status='INVALID'",Integer.class,batch.id())).isEqualTo(1);
        assertThat(persistence.preview(batch.id(),0,20,null,null).rows().content()).hasSize(5);
        assertThatThrownBy(()->upload.upload("again.xlsx",bytes,HrPayrollType.LEADERSHIP,actor)).hasMessageContaining("trước đó");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hr_payroll_campaigns WHERE import_id=?",Integer.class,batch.id())).isZero();
    }
}
