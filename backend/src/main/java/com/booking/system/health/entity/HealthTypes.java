package com.booking.system.health.entity;

public final class HealthTypes {
    private HealthTypes() { }
    public enum WorkforceGroup { INDIRECT, DIRECT, BOTH }
    public enum CampaignStatus { OPEN, CLOSED }
    public enum IndicatorType { NUMBER, TEXT, CODED, COMPOSITE }
    public enum IndicatorStatus { ACTIVE, INACTIVE }
}
