package com.booking.system.hr.api.dto;

import com.booking.system.hr.enums.HrMovementStatus;
import com.booking.system.hr.enums.HrMovementType;
import com.booking.system.hr.enums.HrWorkforceGroup;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public final class HrMovementDtos {
    private HrMovementDtos() { }

    public record SearchRequest(
            String keyword,
            HrMovementType type,
            HrMovementStatus status,
            HrWorkforceGroup workforceGroup,
            String departmentId,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            String recordType
    ) { }
}
