package com.teample.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTodoResponse {
    private String projectId;
    private String projectName;
    private String minutesId;
    private String minutesTitle;
    private String memberName;
    private String task;
    private String deadline;
    private String status;
}
