package com.booking.system.health.api;

import com.booking.system.health.api.HrHealthRecordDtos.*;
import com.booking.system.hr.api.dto.HrPageResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Public-facing contracts deliberately omit internal source rows, actors and review reasons. */
public final class HealthSelfDtos {
    private HealthSelfDtos() { }
    public record Release(String id, int revision, int year, String campaign, String provider, LocalDate examinedOn,
                          String classification, boolean incomplete, LocalDateTime publishedAt) { }
    public record Profile(String name, String code, LocalDate birthDate, HrPageResponse<Release> releases) {
        @Override public String toString() { return "HealthSelfProfile[redacted]"; }
    }
    public record Detail(Release release, HrPageResponse<Measurement> results) {
        @Override public String toString() { return "HealthSelfDetail[redacted]"; }
    }
}
