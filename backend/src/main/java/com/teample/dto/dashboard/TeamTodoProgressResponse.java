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
public class TeamTodoProgressResponse {
    private String todoId;
    private String projectId;
    private String projectName;
    private String minutesId;
    private String minutesTitle;
    private String task;
    private String deadline;
    private String sourceAssignee;
    private List<TodoAssignmentResponse> assignments;
}