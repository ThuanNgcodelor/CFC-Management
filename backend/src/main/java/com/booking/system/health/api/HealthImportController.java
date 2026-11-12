package com.booking.system.health.api;

import com.booking.system.dto.ApiResponse;
import com.booking.system.entity.User;
import com.booking.system.health.api.HrHealthImportDtos.*;
import com.booking.system.health.importer.*;
import com.booking.system.hr.api.HrActorResolver;
import com.booking.system.hr.api.dto.HrPageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/hr/health")
@RequiredArgsConstructor
public class HealthImportController {
    private final HealthImportService service;
    private final HealthImportStaging staging;
    private final HealthImportWriter writer;
    private final HealthSourceCorrectionService corrections;
    private final HrActorResolver actors;

    @PostMapping(value="/imports/inspect",consumes="multipart/form-data")
    public ResponseEntity<ApiResponse<Inspection>> inspect(@AuthenticationPrincipal User user, @RequestPart("file") MultipartFile file,
            @RequestParam(required=false) String sheet, @RequestParam(required=false) Integer headerRow,
            @RequestParam(defaultValue="1") int headerRows) throws IOException {
        return ok(service.inspect(bytes(file),sheet,headerRow,headerRows,actors.fromPrincipal(user)));
    }
    @PostMapping(value="/imports",consumes="multipart/form-data")
    public ResponseEntity<ApiResponse<Batch>> stage(@AuthenticationPrincipal User user, @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("request") StageRequest request) throws IOException { return ok(service.stage(bytes(file),request,actors.fromPrincipal(user))); }
    @GetMapping("/imports")
    public ResponseEntity<ApiResponse<HrPageResponse<Batch>>> batches(@RequestParam String campaignId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return ok(staging.list(campaignId,page,size)); }
    @GetMapping("/imports/{id}/rows")
    public ResponseEntity<ApiResponse<HrPageResponse<PreviewRow>>> rows(@AuthenticationPrincipal User user, @PathVariable String id,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="ALL") String state) { return ok(service.preview(id,page,size,state,actors.fromPrincipal(user))); }
    @PostMapping("/imports/{id}/confirm")
    public ResponseEntity<ApiResponse<ConfirmResult>> confirm(@AuthenticationPrincipal User user,@PathVariable String id,
            @Valid @RequestBody ConfirmRequest request) { return ok(writer.confirm(id,request,actors.fromPrincipal(user))); }
    @PostMapping("/imports/{id}/rows/{rowId}/resolve")
    public ResponseEntity<ApiResponse<Batch>> resolve(@AuthenticationPrincipal User user,@PathVariable String id,@PathVariable String rowId,
            @Valid @RequestBody ResolveRequest request) { return ok(writer.resolve(id,rowId,request,actors.fromPrincipal(user))); }
    @PostMapping("/imports/{id}/rows/{rowId}/correct-classification")
    public ResponseEntity<ApiResponse<Batch>> correctClassification(@AuthenticationPrincipal User user,@PathVariable String id,@PathVariable String rowId,
            @Valid @RequestBody CorrectClassificationRequest request) {return ok(corrections.correct(id,rowId,request,actors.fromPrincipal(user)));}
    @PostMapping("/imports/{id}/cancel")
    public ResponseEntity<ApiResponse<Batch>> cancel(@AuthenticationPrincipal User user,@PathVariable String id,
            @Valid @RequestBody VersionRequest request) { return ok(writer.cancel(id,request,actors.fromPrincipal(user))); }
    @GetMapping("/imports/employees")
    public ResponseEntity<ApiResponse<List<EmployeeChoice>>> employees(@AuthenticationPrincipal User user,@RequestParam String keyword) {
        return ok(service.employees(keyword,actors.fromPrincipal(user)));
    }
    @GetMapping("/mapping-profiles")
    public ResponseEntity<ApiResponse<HrPageResponse<Profile>>> profiles(@AuthenticationPrincipal User user,@RequestParam String fingerprint,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return ok(service.profiles(fingerprint,page,size,actors.fromPrincipal(user))); }
    private static byte[] bytes(MultipartFile file) throws IOException {
        if (file.getSize()>HealthImportSupport.MAX_FILE_BYTES) throw HealthImportSupport.invalid("File vượt giới hạn 10 MB.");
        return file.getBytes();
    }
    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data,"Thao tác nhập sức khỏe thành công."));
    }
}
