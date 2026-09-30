package com.teample.dto.plan;

import com.teample.entity.PlanType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record PlanGrantRequest(
        @NotNull PlanType plan,
        LocalDateTime expiresAt,
        @Size(max = 500) String note
) {
}
