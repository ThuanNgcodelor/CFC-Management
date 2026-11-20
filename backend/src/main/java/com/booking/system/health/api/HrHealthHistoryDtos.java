package com.booking.system.health.api;

import jakarta.validation.constraints.*;
import java.util.List;

public final class HrHealthHistoryDtos {
    private HrHealthHistoryDtos() { }
    public record Batch(String id,String campaignId,int year,String campaign,String sheet,int rows,long imported,long pending,String status,long rowVersion) { }
    public record Status(String state,String message,List<Batch> batches,long imported,long pending) { }
    public record RunRequest(@NotBlank @Size(max=500) String reason) { }
}
