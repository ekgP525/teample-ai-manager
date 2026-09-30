package com.teample.dto.plan;

import com.teample.entity.PlanType;
import com.teample.service.PlanService;

import java.time.LocalDateTime;

public record PlanResponse(
        PlanType plan,
        boolean premium,
        LocalDateTime expiresAt,
        Features features,
        Usage usage
) {
    public record Features(boolean transcription) {
    }

    public record Usage(long monthMinutesUsed, long monthMinutesLimit) {
    }

    public static PlanResponse from(PlanService.PlanSnapshot snapshot) {
        return new PlanResponse(
                snapshot.plan(),
                snapshot.premium(),
                snapshot.expiresAt(),
                new Features(snapshot.premium()),
                new Usage(snapshot.usage().monthMinutesUsed(), snapshot.usage().monthMinutesLimit())
        );
    }
}
