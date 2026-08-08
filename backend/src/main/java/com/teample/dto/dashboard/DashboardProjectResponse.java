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
public class DashboardProjectResponse {
    private String projectId;
    private String projectName;
    private String target;
    private int totalTodoCount;
    private int onTrackTodoCount;
    private int overdueTodoCount;
    private int progressRate;
    private List<DashboardTodoResponse> todos;
}
