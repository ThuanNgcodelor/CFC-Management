package com.booking.system.health.api;

import com.booking.system.dto.ApiResponse;
import com.booking.system.entity.User;
import com.booking.system.health.api.HrHealthRecordDtos.*;
import com.booking.system.health.service.HealthRecordService;
import com.booking.system.hr.api.HrActorResolver;
import com.booking.system.hr.api.dto.HrPageResponse;
import com.booking.system.hr.enums.HrWorkforceGroup;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hr/health/records")
@RequiredArgsConstructor
public class HealthRecordController {
    private final HealthRecordService service;
    private final HrActorResolver actors;
    @GetMapping
    public ResponseEntity<ApiResponse<HrPageResponse<Person>>> people(@AuthenticationPrincipal User user,
        @RequestParam(required=false) String keyword,@RequestParam(required=false) Integer year,@RequestParam(required=false) HrWorkforceGroup group,
        @RequestParam(defaultValue="WITH_DATA") String data,@RequestParam(defaultValue="codeAsc") String sort,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return ok(service.people(keyword,year,group,data,sort,page,size,actors.fromPrincipal(user)));
    }
    @GetMapping("/{employeeId}/timeline")
    public ResponseEntity<ApiResponse<Profile>> timeline(@AuthenticationPrincipal User user,@PathVariable String employeeId,
        @RequestParam int fromYear,@RequestParam int toYear,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return ok(service.timeline(employeeId,fromYear,toYear,page,size,actors.fromPrincipal(user)));
    }
    @GetMapping("/{employeeId}/visits/{visitId}/results")
    public ResponseEntity<ApiResponse<Results>> results(@AuthenticationPrincipal User user,@PathVariable String employeeId,@PathVariable String visitId,
        @RequestParam(required=false) String keyword,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return ok(service.results(employeeId,visitId,keyword,page,size,actors.fromPrincipal(user)));
    }
    @PostMapping("/{employeeId}/compare")
    public ResponseEntity<ApiResponse<Comparison>> compare(@AuthenticationPrincipal User user,@PathVariable String employeeId,@Valid @RequestBody CompareRequest request){
        return ok(service.compare(employeeId,request,actors.fromPrincipal(user)));
    }
    private static <T> ResponseEntity<ApiResponse<T>> ok(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(value,"Đã đọc hồ sơ sức khỏe nội bộ."));}
}
