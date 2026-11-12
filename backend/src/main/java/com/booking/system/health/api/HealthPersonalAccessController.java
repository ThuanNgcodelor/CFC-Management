package com.booking.system.health.api;

import com.booking.system.dto.ApiResponse;
import com.booking.system.entity.User;
import com.booking.system.health.api.HealthPersonalAccessDtos.*;
import com.booking.system.health.service.HealthPersonalAccessService;
import com.booking.system.hr.api.HrActorResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hr/health/personal-access")
@RequiredArgsConstructor
public class HealthPersonalAccessController {
    private final HealthPersonalAccessService access;
    private final HrActorResolver actors;
    @GetMapping("/{employee}")
    public ResponseEntity<ApiResponse<Status>> status(@PathVariable String employee,@AuthenticationPrincipal User user){return ok(access.status(employee,actors.fromPrincipal(user)));}
    @PostMapping("/{employee}/issue")
    public ResponseEntity<ApiResponse<Issued>> issue(@PathVariable String employee,@Valid @RequestBody Change request,@AuthenticationPrincipal User user){return ok(access.issue(employee,request,actors.fromPrincipal(user)));}
    @PostMapping("/{employee}/policy")
    public ResponseEntity<ApiResponse<Void>> policy(@PathVariable String employee,@Valid @RequestBody LookupPolicy request,@AuthenticationPrincipal User user){access.policy(employee,request,actors.fromPrincipal(user));return ok(null);}
    @PostMapping("/{employee}/revoke")
    public ResponseEntity<ApiResponse<Void>> revoke(@PathVariable String employee,@Valid @RequestBody Change request,@AuthenticationPrincipal User user){access.revoke(employee,request,actors.fromPrincipal(user));return ok(null);}
    private static <T> ResponseEntity<ApiResponse<T>> ok(T data){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data,"Đã xử lý mã tra cứu cá nhân."));}
}
