package com.booking.system.health.api;

import com.booking.system.health.entity.HealthTypes.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public final class HrHealthDtos {
    private HrHealthDtos() { }
    public record Capabilities(boolean enabled) { }
    public record CampaignRequest(
            @NotBlank(message = "Nhập mã đợt khám.") @Pattern(regexp = "[A-Za-z0-9_-]{1,40}", message = "Mã đợt khám chỉ gồm chữ, số, gạch ngang và gạch dưới.") String code,
            @NotBlank(message = "Nhập tên đợt khám.") @Size(max = 255) String name,
            @NotNull @Min(1900) @Max(2200) Integer reportYear,
            @NotBlank(message = "Nhập cơ sở khám.") @Size(max = 255) String provider,
            LocalDate startsOn, LocalDate endsOn,
            @NotNull(message = "Chọn khối nhân sự.") WorkforceGroup workforceGroup,
            @PositiveOrZero Long rowVersion) { }
    public record CampaignResponse(String id, String code, String name, Integer reportYear, String provider,
                                   LocalDate startsOn, LocalDate endsOn, WorkforceGroup workforceGroup,
                                   CampaignStatus status, long rowVersion) { }
    public record IndicatorRequest(
            @NotBlank(message = "Nhập mã chỉ tiêu.") @Pattern(regexp = "[A-Za-z0-9_-]{1,40}", message = "Mã chỉ tiêu chỉ gồm chữ, số, gạch ngang và gạch dưới.") String code,
            @NotBlank(message = "Nhập tên chỉ tiêu.") @Size(max = 255) String name,
            @NotBlank(message = "Nhập nhóm chỉ tiêu.") @Size(max = 100) String category,
            @NotNull(message = "Chọn kiểu kết quả.") IndicatorType valueType,
            @NotNull IndicatorStatus status,
            @NotNull @Size(max = 30) List<@NotBlank @Size(max = 255) String> aliases,
            @PositiveOrZero Long rowVersion) { }
    public record IndicatorResponse(String id, String code, String name, String category, IndicatorType valueType,
                                    IndicatorStatus status, List<String> aliases, long rowVersion) { }
}
