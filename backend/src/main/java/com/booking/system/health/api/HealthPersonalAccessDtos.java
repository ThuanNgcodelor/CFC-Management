package com.booking.system.health.api;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public final class HealthPersonalAccessDtos {
    private HealthPersonalAccessDtos() { }
    public record Status(boolean eligible,boolean issued,boolean revoked,LocalDateTime expiresAt,String generation,boolean allowIdentityOnly) { }
    public record Change(@Size(max=36) String generation,@NotBlank @Size(max=500) String reason) {
        @Override public String toString(){return "HealthAccessChange[redacted]";}
    }
    public record LookupPolicy(@NotBlank @Size(max=36) String generation,@NotBlank @Size(max=500) String reason,boolean allowIdentityOnly) {
        @Override public String toString(){return "HealthLookupPolicy[redacted]";}
    }
    public enum LookupMode { IDENTITY, ACCESS_CODE }
    public record Issued(String code,LocalDateTime expiresAt) {
        @Override public String toString(){return "HealthAccessIssued[redacted]";}
    }
    public record Lookup(@Pattern(regexp="[0-9]{12}") String citizenNumber,
                         @Size(max=32) String employeeCode,
                         @Pattern(regexp="[A-Za-z0-9_-]{32}") String accessCode,LookupMode mode) {
        public Lookup(String citizenNumber,String employeeCode,String accessCode){this(citizenNumber,employeeCode,accessCode,LookupMode.ACCESS_CODE);}
        @AssertTrue(message="Nhập CCCD và mã nhân viên, hoặc mã tra cứu riêng theo cách đã chọn.")
        public boolean isLookupComplete(){return effectiveMode()==LookupMode.IDENTITY
            ? citizenNumber!=null&&citizenNumber.matches("[0-9]{12}")&&employeeCode!=null&&!employeeCode.isBlank()
            : accessCode!=null&&accessCode.matches("[A-Za-z0-9_-]{32}");}
        public LookupMode effectiveMode(){return mode==null?LookupMode.ACCESS_CODE:mode;}
        @Override public String toString(){return "HealthLookup[redacted]";}
    }
    public record Session(String token,LocalDateTime expiresAt) {
        @Override public String toString(){return "HealthPersonalSession[redacted]";}
    }
}
