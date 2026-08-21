package com.teample.dto.project;

import com.teample.entity.ProjectMember;
import com.teample.entity.ProjectMemberRole;

import java.time.LocalDateTime;

public record ProjectMemberResponse(
        String userId,
        String displayName,
        ProjectMemberRole role,
        LocalDateTime joinedAt
) {
    public static ProjectMemberResponse from(ProjectMember member) {
        return new ProjectMemberResponse(
                member.getUserId(),
                member.getDisplayName(),
                member.getRole(),
                member.getJoinedAt()
        );
    }
}