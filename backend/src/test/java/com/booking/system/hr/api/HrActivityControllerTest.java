package com.booking.system.hr.api;

import com.booking.system.hr.api.dto.HrRosterResponse;
import com.booking.system.hr.api.dto.HrMovementDtos;
import com.booking.system.hr.api.dto.HrPageResponse;
import com.booking.system.hr.enums.HrMovementStatus;
import com.booking.system.hr.enums.HrMovementType;
import com.booking.system.hr.enums.HrWorkforceGroup;
import com.booking.system.hr.enums.HrRosterStatus;
import com.booking.system.hr.service.HrExcelExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HrActivityControllerTest {

    @Mock
    private HrActivityQueryService queryService;
    @Mock
    private HrExcelExportService exportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new HrActivityController(queryService, exportService))
                .setControllerAdvice(new HrApiExceptionHandler())
                .build();
    }

    @Test
    void movementSearchBindsAllFiltersAndKeepsHttpContract() throws Exception {
        var filters = new HrMovementDtos.SearchRequest("DEMO", HrMovementType.INCREASE,
                HrMovementStatus.CONFIRMED, HrWorkforceGroup.GENERAL_LABOR, "unit-demo",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "ADJUSTMENT");
        when(queryService.movements(1, 50, filters, "employeeName,asc")).thenReturn(HrPageResponse.empty());

        mockMvc.perform(get("/api/v1/hr/movements")
                        .param("keyword", "DEMO").param("type", "INCREASE").param("status", "CONFIRMED")
                        .param("workforceGroup", "GENERAL_LABOR").param("departmentId", "unit-demo")
                        .param("from", "2026-09-01").param("to", "2026-09-30").param("recordType", "ADJUSTMENT")
                        .param("page", "1").param("size", "50").param("sort", "employeeName,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void movementSearchRejectsInvalidDatesBeforeCallingService() throws Exception {
        mockMvc.perform(get("/api/v1/hr/movements").param("from", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(queryService);
    }

    @Test
    void getRosterDetailUsesStableHttpContract() throws Exception {
        HrRosterResponse roster = new HrRosterResponse(
                "roster-2026-06",
                LocalDate.of(2026, 6, 1),
                HrRosterStatus.CLOSED,
                329,
                null,
                null,
                true,
                "checksum",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0L
        );
        when(queryService.roster("roster-2026-06")).thenReturn(roster);

        mockMvc.perform(get("/api/v1/hr/rosters/roster-2026-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value("roster-2026-06"))
                .andExpect(jsonPath("$.data.periodStart").value("2026-06-01"))
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.itemCount").value(329));
    }

    @Test
    void exportMonthReturnsXlsxAttachment() throws Exception {
        byte[] content = new byte[] {'P', 'K', 3, 4};
        when(exportService.exportMonth(2026, 6))
                .thenReturn(new HrExcelExportService.ExportFile("hr-T6-26.xlsx", content));

        mockMvc.perform(get("/api/v1/hr/exports/month")
                        .param("year", "2026")
                        .param("month", "6"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"hr-T6-26.xlsx\""))
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
}
