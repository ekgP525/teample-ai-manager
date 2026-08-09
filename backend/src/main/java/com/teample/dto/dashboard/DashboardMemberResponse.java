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
public class DashboardMemberResponse {
    private String userId;
    private String memberName;
    private int totalTodoCount;
    private int completedTodoCount;
    private int pendingTodoCount;
    private int onTrackTodoCount;
    private int overdueTodoCount;
    private int noDeadlineTodoCount;
    private int unknownDeadlineTodoCount;
    private int progressRate;
    private List<DashboardTodoResponse> todos;
}