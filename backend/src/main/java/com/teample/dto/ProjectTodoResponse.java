package com.teample.dto;

import com.teample.entity.TodoStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectTodoResponse {
    private String id;
    private String content;
    private TodoAssigneeResponse assignee;
    private String meetingNoteId;
    private TodoStatus status;
    private Integer priorityOrder;
    private LocalDate dueDate;
    private String completedAt;
    private String createdAt;
    private String updatedAt;
}
