package com.teample.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "project_todos",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_todo_source",
                columnNames = {"project_id", "minutes_id", "source_index"}
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "minutes_id", nullable = false)
    private Minutes minutes;

    @Column(name = "source_index", nullable = false)
    private Integer sourceIndex;

    @Column(name = "source_assignee")
    private String sourceAssignee;

    @Column(columnDefinition = "text", nullable = false)
    private String task;

    private String deadline;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
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