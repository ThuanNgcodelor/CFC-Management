package com.booking.system.health.service;

import com.booking.system.health.api.HealthPersonalAccessDtos.*;
import com.booking.system.health.repository.HealthPersonalAccessRepository;
import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.importer.HrImportActor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import static com.booking.system.health.importer.HealthImportSupport.hash;

@Service
@RequiredArgsConstructor
public class HealthPersonalAccessService {
    private final HealthAccess access;
    private final HealthPersonalAccessRepository repository;
    private final HealthAuditService audit;
    private final HealthLookupLimiter limiter;
    private static final SecureRandom RANDOM=new SecureRandom();
    @Value("${cfc.health.self-service.enabled:false}") private boolean enabled;
    @Value("${cfc.health.self-service.grant-days:90}") private int grantDays;
    @Value("${cfc.health.self-service.session-minutes:15}") private int sessionMinutes;

    @Transactional(readOnly=true)
    public Status status(String employee,HrImportActor actor) {
        access.requireManager(actor);
        var grant=repository.grant(employee);
        // Eligibility is read separately so administrators do not receive identity numbers.
        boolean eligible=repository.eligible(employee);
        return grant.map(g->new Status(eligible,true,g.revoked(),g.expires(),g.generation(),g.allowIdentityOnly())).orElseGet(()->new Status(eligible,false,false,null,null,false));
    }
    @Transactional
    public Issued issue(String employee,Change request,HrImportActor actor) {
        access.requireManager(actor);validateSettings();
        if(repository.lockEligible(employee)==null)throw HrApiException.badRequest("HEALTH_ACCESS_IDENTITY","Hồ sơ cần CCCD 12 chữ số hợp lệ trước khi cấp mã. Hãy kiểm tra trong phân hệ nhân sự.");
        version(employee,request.generation());String code=random(24);LocalDateTime now=now(),expires=now.plusDays(grantDays);
        repository.issue(employee,hash(code),UUID.randomUUID().toString(),expires,now);
        audit.record("PERSONAL_ACCESS_ISSUED",employee,actor,request.reason());return new Issued(code,expires);
    }
    @Transactional
    public void revoke(String employee,Change request,HrImportActor actor) {
        access.requireManager(actor);
        if(!repository.lockEmployee(employee))throw HrApiException.badRequest("HEALTH_ACCESS_IDENTITY","Không tìm thấy hồ sơ.");
        version(employee,request.generation());repository.revoke(employee,now());audit.record("PERSONAL_ACCESS_REVOKED",employee,actor,request.reason());
    }
    @Transactional
    public void policy(String employee,LookupPolicy request,HrImportActor actor) {
        access.requireManager(actor);
        if(!repository.lockEmployee(employee))throw denied();
        version(employee,request.generation());
        var grant=repository.grant(employee).orElseThrow(HealthPersonalAccessService::denied);
        if(grant.revoked()||!grant.expires().isAfter(now()))throw HrApiException.conflict("HEALTH_ACCESS_INACTIVE","Cần cấp quyền tra cứu còn hiệu lực trước khi đổi phương thức.");
        repository.policy(employee,request.allowIdentityOnly(),UUID.randomUUID().toString(),now());
        audit.record(request.allowIdentityOnly()?"ALLOW_IDENTITY_LOOKUP":"REQUIRE_PRIVATE_LOOKUP",employee,actor,request.reason());
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Session lookup(Lookup request,String remoteAddress) {
        requireEnabled();validateSettings();
        boolean identity=request.effectiveMode()==LookupMode.IDENTITY;
        String key=identity&&request.employeeCode()!=null?request.employeeCode().strip():request.accessCode();
        limiter.attempt(remoteAddress,(identity?"identity:":"private:")+(key==null?"":identity?key.toUpperCase(Locale.ROOT):key));
        if(!request.isLookupComplete())throw denied();
        var candidate=identity?repository.grantByCode(request.employeeCode().strip()):repository.grantBySecretHash(hash(request.accessCode()));
        boolean verified=candidate.isPresent()&&(identity
            ?candidate.get().allowIdentityOnly()&&equal(candidate.get().citizen(),request.citizenNumber())
            :equal(candidate.get().hash(),hash(request.accessCode())));
        if(!verified||candidate.get().revoked()||!candidate.get().expires().isAfter(now()))throw denied();
        var grant=candidate.get();
        if(!repository.lockEmployee(grant.employee()))throw denied();
        var fresh=repository.grant(grant.employee()).orElseThrow(HealthPersonalAccessService::denied);
        if(fresh.revoked()||!fresh.generation().equals(grant.generation())||!fresh.expires().isAfter(now()))throw denied();
        String token=random(32);LocalDateTime now=now(),expires=now.plusMinutes(sessionMinutes);
        if(expires.isAfter(fresh.expires()))expires=fresh.expires();
        repository.session(hash(token),grant.employee(),grant.generation(),expires,now);return new Session(token,expires);
    }
    @Transactional(readOnly=true)
    public String owner(String token) {
        requireEnabled();if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw denied();
        return repository.owner(hash(token),now()).orElseThrow(HealthPersonalAccessService::denied);
    }
    @Transactional
    public void logout(String token){requireEnabled();if(token!=null&&token.matches("[A-Za-z0-9_-]{43}"))repository.logout(hash(token));}
    public void requireEnabled(){access.requireEnabled();if(!enabled)throw HrApiException.notFound("HEALTH_SELF_DISABLED","Tra cứu sức khỏe cá nhân chưa được bật.");}
    private void version(String employee,String expected) {
        String actual=repository.grant(employee).map(HealthPersonalAccessRepository.Grant::generation).orElse(null);
        if(!Objects.equals(actual,expected))throw HrApiException.conflict("HEALTH_ACCESS_CHANGED","Mã đã được quản trị viên khác thay đổi. Hãy tải lại.");
    }
    private void validateSettings(){if(grantDays<1||grantDays>365||sessionMinutes<1||sessionMinutes>30)throw new IllegalStateException("Health access expiry configuration outside supported bounds");}
    private static boolean equal(String a,String b){return a!=null&&b!=null&&MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
    private static String random(int bytes){byte[] value=new byte[bytes];RANDOM.nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
    private static LocalDateTime now(){return LocalDateTime.now(ZoneOffset.UTC);}
    private static HrApiException denied(){return new HrApiException(HttpStatus.UNAUTHORIZED,"HEALTH_SELF_DENIED","Không thể xác minh thông tin hoặc phiên đã hết hạn. Vui lòng kiểm tra lại hoặc liên hệ nhân sự.");}
}
