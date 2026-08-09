package com.teample.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "todo_member_progress",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_todo_member_progress",
                columnNames = {"todo_id", "user_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodoMemberProgress {

    public static final String STATUS_TODO = "TODO";
    public static final String STATUS_DONE = "DONE";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "todo_id", nullable = false)
    private ProjectTodo todo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "minutes_id", nullable = false)
    private Minutes minutes;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "member_name", nullable = false)
    private String memberName;

    @Column(nullable = false)
    private Boolean assigned;

    @Column(nullable = false)
    private Boolean completed;

    @Column(nullable = false)
    private String status;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (assigned == null) assigned = true;
        if (completed == null) completed = false;
        if (status == null) status = completed ? STATUS_DONE : STATUS_TODO;
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void setCompletedState(boolean completed) {
        this.completed = completed;
        this.status = completed ? STATUS_DONE : STATUS_TODO;
        this.completedAt = completed ? LocalDateTime.now() : null;
    }
}