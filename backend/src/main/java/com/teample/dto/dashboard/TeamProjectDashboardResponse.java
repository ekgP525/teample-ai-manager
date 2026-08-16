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
public class TeamProjectDashboardResponse {
    private String projectId;
    private String projectName;
    private int totalTodoCount;
    private int completedTodoCount;
    private int pendingTodoCount;
    private int progressRate;
    private List<TeamMemberProgressResponse> members;
    private List<TeamTodoProgressResponse> todos;
}
