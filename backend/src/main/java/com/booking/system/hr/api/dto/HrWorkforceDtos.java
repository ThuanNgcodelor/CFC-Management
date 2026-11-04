package com.booking.system.hr.api.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class HrWorkforceDtos {
    private HrWorkforceDtos() {
    }

    public record IntakeStatusRequest(
            @PositiveOrZero long rowVersion,
            @Size(max = 500) String reason
    ) {
    }
}
