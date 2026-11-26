package com.booking.system.hr;

import com.booking.system.hr.service.*;
import com.booking.system.hr.entity.*;
import com.booking.system.hr.enums.*;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HrLeadershipPayrollImportTest {
    final HrPayrollImportRepository imports=mock(HrPayrollImportRepository.class);
    final HrPayrollImportRowRepository rows=mock(HrPayrollImportRowRepository.class);
    final HrEmployeeRepository people=mock(HrEmployeeRepository.class);
    final HrEmployeeTelegramBindingRepository bindings=mock(HrEmployeeTelegramBindingRepository.class);
    final HrPayrollService persistence=new HrPayrollService(imports,rows,people,bindings);
    final HrImportActor actor=new HrImportActor("fixture","Fixture","ADMIN");
    @Test void invalidAmountsNeverBecomeReadyWhenRefreshingBindings() throws Exception {
        var book=HrLeadershipPayrollParserTest.workbook(1);book.getSheetAt(0).getRow(2).getCell(3).setCellErrorValue(org.apache.poi.ss.usermodel.FormulaError.REF.getCode());
        var parsed=new HrLeadershipPayrollParser().parse(HrLeadershipPayrollParserTest.bytes(book));
        when(imports.save(any())).thenAnswer(i->{HrPayrollImport value=i.getArgument(0);value.setId("batch");return value;});
        var response=persistence.persist("fixture.xlsx",parsed,HrPayrollType.LEADERSHIP,actor);
        assertThat(response.payrollType()).isEqualTo(HrPayrollType.LEADERSHIP);assertThat(response.invalidRows()).isEqualTo(1);assertThat(response.validRows()).isZero();assertThat(response.readyRows()).isZero();
        var saved=ArgumentCaptor.forClass(Iterable.class);verify(rows).saveAll(saved.capture());
        HrPayrollImportRow row=(HrPayrollImportRow)saved.getValue().iterator().next();assertThat(row.getStatus()).isEqualTo(HrPayrollRowStatus.INVALID);
        var batch=new HrPayrollImport();batch.setId("batch");batch.setInvalidRows(1);when(imports.findById("batch")).thenReturn(Optional.of(batch));when(rows.findByPayrollImportIdOrderBySourceRowNumber("batch")).thenReturn(List.of(row));
        persistence.refreshEligibility("batch",actor);assertThat(row.getStatus()).isEqualTo(HrPayrollRowStatus.INVALID);assertThat(row.getTelegramChatId()).isNull();
    }
    @Test void validAmountsUseExistingEmployeeAndVerifiedBindingWithoutSending() throws Exception {
        var parsed=new HrLeadershipPayrollParser().parse(HrLeadershipPayrollParserTest.bytes(HrLeadershipPayrollParserTest.workbook(1)));
        var employee=new HrEmployee();employee.setId("person");employee.setEmployeeCode("DEMO1");
        var binding=new HrEmployeeTelegramBinding();binding.setEmployee(employee);binding.setStatus(HrTelegramBindingStatus.ACTIVE);binding.setTelegramChatId(123L);binding.setTelegramUserId(456L);
        when(people.findAllByEmployeeCodeIn(any())).thenReturn(List.of(employee));when(bindings.findAllByEmployeeIdIn(any())).thenReturn(List.of(binding));when(imports.save(any())).thenAnswer(i->i.getArgument(0));
        var response=persistence.persist("fixture.xlsx",parsed,HrPayrollType.LEADERSHIP,actor);assertThat(response.readyRows()).isEqualTo(1);assertThat(response.invalidRows()).isZero();
        verify(rows,never()).save(any());verify(rows).saveAll(any());
    }
    @Test void sourceHashStillPreventsDuplicateImport() throws Exception {
        var parsed=new HrLeadershipPayrollParser().parse(HrLeadershipPayrollParserTest.bytes(HrLeadershipPayrollParserTest.workbook(1)));
        when(imports.findByFileSha256(parsed.sha256())).thenReturn(Optional.of(new HrPayrollImport()));
        assertThatThrownBy(()->persistence.persist("fixture.xlsx",parsed,HrPayrollType.LEADERSHIP,actor)).hasMessageContaining("trước đó");verifyNoInteractions(rows,people,bindings);
    }
}
