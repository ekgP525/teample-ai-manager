package com.teample.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTodoResponse {
    private String assignmentId;
    private String todoId;
    private String projectId;
    private String projectName;
    private String minutesId;
    private String minutesTitle;
    private String userId;
    private String memberName;
    private String task;
    private String deadline;
    private Boolean assigned;
    private Boolean completed;
    private String status;
    private LocalDateTime completedAt;
    private String deadlineStatus;
}