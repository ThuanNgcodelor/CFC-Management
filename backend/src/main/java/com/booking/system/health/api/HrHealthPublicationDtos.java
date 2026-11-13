package com.booking.system.health.api;

import com.booking.system.health.api.HrHealthRecordDtos.*;
import com.booking.system.hr.enums.HrWorkforceGroup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class HrHealthPublicationDtos {
    private HrHealthPublicationDtos() { }
    public record Scope(@NotBlank @Size(max=36) String campaignId,@Size(max=100) String keyword,HrWorkforceGroup group,
            @Size(max=200) List<@NotBlank @Size(max=36) String> employeeIds) {
        @Override public String toString(){return "HealthPublicationScope[redacted]";}
    }
    public record Campaign(String id,String name,int year,String status,long rowVersion) { }
    public record PublicationInfo(String id,String visitId,int revision,String status,long rowVersion,String contentHash,
            String publishReason,String revokeReason,LocalDateTime createdAt,String createdByActor) {
        @Override public String toString(){return "HealthPublicationInfo[redacted]";}
    }
    public record Candidate(String visitId,String sid,long rowVersion,String token,String state,int results,
            boolean reviewed,boolean publishable,List<String> blockers,List<String> warnings,PublicationInfo latest) {
        @Override public String toString(){return "HealthPublicationCandidate[redacted]";}
    }
    public record PersonPreview(String employeeId,String code,String name,List<Candidate> visits) {
        @Override public String toString(){return "HealthPublicationPerson[redacted]";}
    }
    public record Preview(Campaign campaign,int people,int visits,int publishable,int blocked,List<PersonPreview> rows) {
        @Override public String toString(){return "HealthPublicationPreview[redacted]";}
    }
    public record Decision(@NotBlank @Size(max=36) String visitId,@NotBlank @Pattern(regexp="[a-f0-9]{64}") String token) { }
    public record PersonDecision(@NotBlank @Size(max=36) String employeeId,@NotEmpty @Size(max=20) List<@Valid Decision> visits) { }
    public record BatchRequest(@NotBlank @Size(max=36) String campaignId,@NotEmpty @Size(max=20) List<@Valid PersonDecision> people,
            @NotBlank @Size(max=500) String reason,boolean acknowledgeIncomplete) {
        @Override public String toString(){return "HealthPublicationBatch[redacted]";}
    }
    public record Outcome(String employeeId,String status,int affected,String message) { }
    public record BatchResult(int succeeded,int skipped,int failed,List<Outcome> outcomes) { }
    public record RevokeRequest(@NotBlank @Size(max=36) String employeeId,@Min(0) long rowVersion,@NotBlank @Size(max=500) String reason) { }
    public record CampaignStateRequest(@Min(0) long rowVersion,boolean closed,@NotBlank @Size(max=500) String reason) { }
    public record PublishedSnapshot(int format,Person person,Visit visit,List<Measurement> results,boolean incomplete,String reviewReason) {
        @Override public String toString(){return "HealthPublishedSnapshot[redacted]";}
    }
    public record PublishedResults(PublicationInfo publication,Person person,Visit visit,com.booking.system.hr.api.dto.HrPageResponse<Measurement> results) {
        @Override public String toString(){return "HealthPublishedResults[redacted]";}
    }
}
