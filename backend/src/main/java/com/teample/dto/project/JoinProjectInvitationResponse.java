package com.teample.dto.project;

import com.teample.entity.ProjectMemberRole;

public record JoinProjectInvitationResponse(String projectId, String projectName, ProjectMemberRole role) {
}