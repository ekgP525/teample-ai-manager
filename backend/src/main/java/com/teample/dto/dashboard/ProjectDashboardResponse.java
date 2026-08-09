package com.teample.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDashboardResponse {
    private String projectId;
    private String projectName;
    private String selectedUserId;
    private String selectedMemberName;
    private DashboardMemberResponse selectedMember;
    private List<DashboardMemberResponse> teamMembers;
}