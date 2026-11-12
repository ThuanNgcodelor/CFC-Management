package com.booking.system.health.api;

import com.booking.system.dto.ApiResponse;
import com.booking.system.entity.User;
import com.booking.system.health.api.HrHealthPublicationDtos.*;
import com.booking.system.health.service.*;
import com.booking.system.hr.api.HrActorResolver;
import com.booking.system.hr.api.dto.HrPageResponse;
import com.booking.system.hr.enums.HrWorkforceGroup;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hr/health/publications")
@RequiredArgsConstructor
public class HealthPublicationController {
    private final HealthPublicationReader reader;
    private final HealthPublicationService service;
    private final HealthPublicationWriter writer;
    private final HealthPublishedReader published;
    private final HealthPublishedExport exports;
    private final HealthCampaignStateService states;
    private final HrActorResolver actors;
    @GetMapping("/candidates")
    public ResponseEntity<ApiResponse<HrPageResponse<PersonPreview>>> candidates(@AuthenticationPrincipal User user,@RequestParam String campaignId,
            @RequestParam(required=false) String keyword,@RequestParam(required=false) HrWorkforceGroup group,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        if(keyword!=null&&keyword.length()>100)throw com.booking.system.hr.api.HrApiException.badRequest("HEALTH_PUBLICATION_SEARCH","Từ khóa tối đa 100 ký tự.");
        return ok(reader.candidates(new Scope(campaignId,keyword,group,null),page,size,actors.fromPrincipal(user)));
    }
    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<Preview>> preview(@AuthenticationPrincipal User user,@Valid @RequestBody Scope scope){return ok(reader.preview(scope,actors.fromPrincipal(user)));}
    @PostMapping("/review")
    public ResponseEntity<ApiResponse<BatchResult>> review(@AuthenticationPrincipal User user,@Valid @RequestBody BatchRequest request){return ok(service.apply(request,true,actors.fromPrincipal(user)));}
    @PostMapping("/publish")
    public ResponseEntity<ApiResponse<BatchResult>> publish(@AuthenticationPrincipal User user,@Valid @RequestBody BatchRequest request){return ok(service.apply(request,false,actors.fromPrincipal(user)));}
    @PostMapping("/revoke-batch")
    public ResponseEntity<ApiResponse<BatchResult>> revokeBatch(@AuthenticationPrincipal User user,@Valid @RequestBody BatchRequest request){return ok(service.revoke(request,actors.fromPrincipal(user)));}
    @PostMapping("/{id}/revoke")
    public ResponseEntity<ApiResponse<PublicationInfo>> revoke(@AuthenticationPrincipal User user,@PathVariable String id,@Valid @RequestBody RevokeRequest request){return ok(writer.revoke(id,request,actors.fromPrincipal(user)));}
    @PostMapping("/campaigns/{id}/state")
    public ResponseEntity<ApiResponse<Campaign>> campaignState(@AuthenticationPrincipal User user,@PathVariable String id,@Valid @RequestBody CampaignStateRequest request){return ok(states.set(id,request,actors.fromPrincipal(user)));}
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<HrPageResponse<PublicationInfo>>> history(@AuthenticationPrincipal User user,@RequestParam String employeeId,@RequestParam String visitId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ok(published.history(employeeId,visitId,page,size,actors.fromPrincipal(user)));}
    @GetMapping("/{id}/results")
    public ResponseEntity<ApiResponse<PublishedResults>> results(@AuthenticationPrincipal User user,@PathVariable String id,@RequestParam String employeeId,@RequestParam(required=false) String keyword,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ok(published.results(employeeId,id,keyword,page,size,actors.fromPrincipal(user)));}
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal User user,@PathVariable String id,@RequestParam String employeeId) {
        byte[] bytes=exports.export(employeeId,id,actors.fromPrincipal(user));return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=So_suc_khoe_cong_bo.pdf").header("X-Content-Type-Options","nosniff").body(bytes);
    }
    private static <T> ResponseEntity<ApiResponse<T>> ok(T data){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data,"Đã xử lý phiên bản sức khỏe nội bộ."));}
}
