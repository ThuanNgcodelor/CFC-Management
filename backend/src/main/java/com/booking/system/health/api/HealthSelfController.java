package com.booking.system.health.api;

import com.booking.system.dto.ApiResponse;
import com.booking.system.health.api.HealthPersonalAccessDtos.*;
import com.booking.system.health.api.HealthSelfDtos.Profile;
import com.booking.system.health.api.HealthSelfDtos.Detail;
import com.booking.system.health.api.HrHealthRecordDtos.*;
import com.booking.system.health.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health-self")
@RequiredArgsConstructor
public class HealthSelfController {
    private final HealthPersonalAccessService access;
    private final HealthSelfRecordService records;
    @GetMapping("/capabilities")
    public ResponseEntity<ApiResponse<Map<String,Boolean>>> capabilities(){access.requireEnabled();return ok(Map.of("enabled",true));}
    @PostMapping("/session")
    public ResponseEntity<ApiResponse<Session>> lookup(@Valid @RequestBody Lookup request,HttpServletRequest servlet){return ok(access.lookup(request,servlet.getRemoteAddr()));}
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader(value="X-Health-Session",required=false) String token){access.logout(token);return ok(null);}
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<Profile>> profile(@RequestHeader(value="X-Health-Session",required=false) String token,
        @RequestParam int from,@RequestParam int to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return ok(records.profile(access.owner(token),from,to,page,size));}
    @GetMapping("/releases/{id}")
    public ResponseEntity<ApiResponse<Detail>> detail(@RequestHeader(value="X-Health-Session",required=false) String token,@PathVariable String id,
        @RequestParam(required=false) String keyword,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ok(records.detail(access.owner(token),id,keyword,page,size));}
    @PostMapping("/compare")
    public ResponseEntity<ApiResponse<Comparison>> compare(@RequestHeader(value="X-Health-Session",required=false) String token,@Valid @RequestBody CompareRequest request){return ok(records.compare(access.owner(token),request.visitIds(),request.keyword(),request.page(),request.size()));}
    @GetMapping("/releases/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@RequestHeader(value="X-Health-Session",required=false) String token,@PathVariable String id){
        String owner=access.owner(token);byte[] bytes=records.pdf(owner,id);access.owner(token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=So_suc_khoe_ca_nhan.pdf").body(bytes);
    }
    private static <T> ResponseEntity<ApiResponse<T>> ok(T data){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data,"Đã xử lý tra cứu sức khỏe cá nhân."));}
}
