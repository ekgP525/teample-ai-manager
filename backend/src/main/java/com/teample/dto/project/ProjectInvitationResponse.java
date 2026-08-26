package com.teample.dto.project;

import com.teample.entity.ProjectInvitation;

import java.time.LocalDateTime;

public record ProjectInvitationResponse(String code, LocalDateTime expiresAt) {
    public static ProjectInvitationResponse from(ProjectInvitation invitation) {
        return new ProjectInvitationResponse(invitation.getCode(), invitation.getExpiresAt());
    }
}