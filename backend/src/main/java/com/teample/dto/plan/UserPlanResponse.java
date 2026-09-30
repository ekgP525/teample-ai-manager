package com.teample.dto.plan;

import com.teample.entity.PlanType;
import com.teample.entity.UserPlan;

import java.time.LocalDateTime;

public record UserPlanResponse(
        String userId,
        PlanType plan,
        LocalDateTime expiresAt,
        String note,
        String grantedBy,
        LocalDateTime updatedAt
) {
    public static UserPlanResponse from(UserPlan plan) {
        return new UserPlanResponse(
                plan.getUserId(),
                plan.getPlan(),
                plan.getExpiresAt(),
                plan.getNote(),
                plan.getGrantedBy(),
                plan.getUpdatedAt()
        );
    }
}
