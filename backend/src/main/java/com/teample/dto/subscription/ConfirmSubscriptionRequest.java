package com.teample.dto.subscription;

import jakarta.validation.constraints.NotBlank;

public record ConfirmSubscriptionRequest(@NotBlank String authKey, @NotBlank String customerKey) {
}
