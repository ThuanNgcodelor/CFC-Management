package com.booking.system.health.api;

import com.booking.system.dto.ApiResponse;
import com.booking.system.entity.User;
import com.booking.system.health.api.HrHealthReportDtos.*;
import com.booking.system.health.service.HealthReportService;
import com.booking.system.hr.api.HrActorResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hr/health/reports")
@RequiredArgsConstructor
public class HealthReportController {
    private final HealthReportService reports;
    private final HrActorResolver actors;
    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<Summary>> preview(@AuthenticationPrincipal User user,@Valid @RequestBody Request request){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(reports.preview(request,actors.fromPrincipal(user)),"Đã thống kê phạm vi báo cáo nội bộ."));}
    @PostMapping("/excel")
    public ResponseEntity<byte[]> excel(@AuthenticationPrincipal User user,@Valid @RequestBody Request request){return file(reports.export(request,false,actors.fromPrincipal(user)),request,false);}
    @PostMapping("/pdf")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal User user,@Valid @RequestBody Request request){return file(reports.export(request,true,actors.fromPrincipal(user)),request,true);}
    private static ResponseEntity<byte[]> file(byte[] data,Request scope,boolean pdf) {
        String name="So_suc_khoe_"+scope.fromYear()+"_"+scope.toYear()+(pdf?".pdf":".xlsx");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name).build().toString())
            .header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType(pdf?"application/pdf":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(data);
    }
}
