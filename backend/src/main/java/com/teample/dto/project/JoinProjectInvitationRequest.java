package com.teample.dto.project;

import jakarta.validation.constraints.NotBlank;

public record JoinProjectInvitationRequest(@NotBlank String code) {
}