package com.teample.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "integrated_todos",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_integrated_todos_minutes_source",
                columnNames = {"minutes_id", "source_index"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectTodo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "minutes_id")
    private Minutes minutes;

    @Column(name = "source_index")
    private Integer sourceIndex;

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
