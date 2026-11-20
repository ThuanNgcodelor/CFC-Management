package com.booking.system.health.api;

import com.booking.system.dto.ApiResponse;
import com.booking.system.entity.User;
import com.booking.system.health.api.HrHealthHistoryDtos.*;
import com.booking.system.health.importer.history.HealthHistoryImport;
import com.booking.system.hr.api.HrActorResolver;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/hr/health/history-import")
@RequiredArgsConstructor
public class HealthHistoryController {
    private final HealthHistoryImport history;
    private final HrActorResolver actors;
    @GetMapping public ResponseEntity<ApiResponse<Status>> status(@AuthenticationPrincipal User user){return response(history.status(actors.fromPrincipal(user)));}
    @PostMapping(value="/key",consumes="multipart/form-data")
    public ResponseEntity<ApiResponse<Status>> unlock(@AuthenticationPrincipal User user,@RequestPart("file") MultipartFile file) throws IOException {
        if(file.getSize()!=32)throw com.booking.system.hr.api.HrApiException.badRequest("HEALTH_HISTORY_KEY","Chọn đúng file khóa lịch sử 32 byte được cấp riêng; không chọn Excel.");
        return response(history.unlock(file.getBytes(),actors.fromPrincipal(user)));
    }
    @PostMapping("/run") public ResponseEntity<ApiResponse<Status>> run(@AuthenticationPrincipal User user,@Valid @RequestBody RunRequest request) {
        return response(history.start(actors.fromPrincipal(user),true,request.reason()));
    }
    private static ResponseEntity<ApiResponse<Status>> response(Status value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(value,"Đã kiểm tra dữ liệu lịch sử."));}
}
