package com.booking.system.infrastructure.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

public final class RouterConnectionDtos {
    private RouterConnectionDtos() {}

    public record Request(@NotBlank @Size(max=200) String baseUrl,
            @NotBlank @Size(max=80) String username,
            @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) @Size(max=256) String password,
            boolean enabled, @Min(0) long rowVersion) {
        @Override public String toString() { return "RouterConnectionRequest[REDACTED]"; }
    }
    public record Response(String baseUrl, String username, boolean enabled,
            boolean passwordConfigured, long rowVersion, String source) {}
    public record TestResponse(boolean connected, String message, InfrastructureDtos.StatusResponse status) {}
}
