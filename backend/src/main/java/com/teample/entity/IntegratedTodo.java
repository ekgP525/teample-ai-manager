package com.teample.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "integrated_todos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegratedTodo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "minutes_id")
    private Minutes minutes;

    /** 회의록 JSON 안의 위치. 표시 순서 용도이며 정체성은 sourceTodoId가 맡는다. */
    @Column(name = "source_index")
    private Integer sourceIndex;

    /** 회의록 JSON 안 TodoData.id. 편집으로 순서가 바뀌어도 같은 업무를 가리킨다. */
    @Column(name = "source_todo_id", length = 64)
    private String sourceTodoId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "assignee_id")
    private String assigneeId;

    @Column(name = "assignee_name", nullable = false)
    private String assigneeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TodoStatus status = TodoStatus.TODO;

    @Column(name = "priority_order", nullable = false)
    private Integer priorityOrder;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
