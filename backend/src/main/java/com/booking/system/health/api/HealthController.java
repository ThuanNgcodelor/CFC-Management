package com.booking.system.health.api;

import com.booking.system.dto.ApiResponse;
import com.booking.system.entity.User;
import com.booking.system.health.api.HrHealthDtos.*;
import com.booking.system.health.entity.HealthTypes.WorkforceGroup;
import com.booking.system.health.service.*;
import com.booking.system.hr.api.HrActorResolver;
import com.booking.system.hr.api.dto.HrPageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hr/health")
@RequiredArgsConstructor
public class HealthController {
    private final HealthCampaignService campaigns;
    private final HealthIndicatorService indicators;
    private final HrActorResolver actors;
    private final HealthAccess access;

    @GetMapping("/capabilities")
    public ResponseEntity<ApiResponse<Capabilities>> capabilities() { return ok(new Capabilities(access.enabled())); }
    @GetMapping("/campaigns")
    public ResponseEntity<ApiResponse<HrPageResponse<CampaignResponse>>> campaigns(
            @RequestParam(required = false) String keyword, @RequestParam(required = false) Integer year,
            @RequestParam(required = false) WorkforceGroup group, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return ok(campaigns.list(keyword, year, group, page, size)); }
    @PostMapping("/campaigns")
    public ResponseEntity<ApiResponse<CampaignResponse>> createCampaign(@AuthenticationPrincipal User principal,
            @Valid @RequestBody CampaignRequest request) { return ok(campaigns.create(request, actors.fromPrincipal(principal))); }
    @PutMapping("/campaigns/{id}")
    public ResponseEntity<ApiResponse<CampaignResponse>> updateCampaign(@AuthenticationPrincipal User principal,
            @PathVariable String id, @Valid @RequestBody CampaignRequest request) { return ok(campaigns.update(id, request, actors.fromPrincipal(principal))); }
    @GetMapping("/indicators")
    public ResponseEntity<ApiResponse<HrPageResponse<IndicatorResponse>>> indicators(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return ok(indicators.list(keyword, page, size)); }
    @PostMapping("/indicators")
    public ResponseEntity<ApiResponse<IndicatorResponse>> createIndicator(@AuthenticationPrincipal User principal,
            @Valid @RequestBody IndicatorRequest request) { return ok(indicators.create(request, actors.fromPrincipal(principal))); }
    @PutMapping("/indicators/{id}")
    public ResponseEntity<ApiResponse<IndicatorResponse>> updateIndicator(@AuthenticationPrincipal User principal,
            @PathVariable String id, @Valid @RequestBody IndicatorRequest request) { return ok(indicators.update(id, request, actors.fromPrincipal(principal))); }
    private static <T> ResponseEntity<ApiResponse<T>> ok(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(value, "Thao tác sổ sức khỏe thành công."));
    }
}
