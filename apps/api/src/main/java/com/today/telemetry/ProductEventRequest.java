package com.today.telemetry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ProductEventRequest(
    @NotBlank @Size(max = 80) String eventName,
    @Size(max = 16) String platform,
    @Min(1) @Max(10) Integer eventSchemaVersion,
    Instant occurredAt,
    @Size(max = 40) String screen,
    @Size(max = 40) String entityType,
    @Size(max = 128) String entityId,
    @Size(max = 128) String sessionId,
    @Size(max = 128) String clientRequestId,
    @Size(max = 32) String appVersion,
    @Size(max = 4000) String propertiesJson) {}
