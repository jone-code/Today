package com.today.agent;

import jakarta.validation.constraints.Size;

public record AgentProfileUpdateRequest(
    @Size(min = 1, max = 64) String name,
    @Size(max = 64) String tone) {}
