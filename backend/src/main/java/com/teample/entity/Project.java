package com.teample.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private List<String> members;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.ACTIVE;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public ProjectStatus getResolvedStatus() {
        return status != null ? status : ProjectStatus.ACTIVE;
    }

    public boolean isDeleted() {
        return getResolvedStatus().isDeleted();
    }

    public boolean isVisibleInActiveList() {
        return getResolvedStatus().isVisibleInActiveList();
    }

    public boolean hasEndDatePassed(LocalDate today) {
        return endDate != null && endDate.isBefore(today);
    }

    public boolean blocksNewMinutes(LocalDate today) {
        return getResolvedStatus().blocksNewMinutes() || hasEndDatePassed(today);
    }

    public void synchronizeLifecycle(LocalDate today, LocalDateTime now) {
        if (isDeleted()) {
            return;
        }
        if (endDate == null) {
            status = ProjectStatus.ACTIVE;
            endedAt = null;
        } else if (endDate.isBefore(today)) {
            status = ProjectStatus.ENDED;
            if (endedAt == null) {
                endedAt = now;
            }
        } else {
            status = ProjectStatus.END_SCHEDULED;
            endedAt = null;
        }
    }

    public void markDeleted(LocalDateTime now) {
        status = ProjectStatus.DELETED;
        deletedAt = now;
    }

    public void restore(LocalDate today, LocalDateTime now) {
        deletedAt = null;
        status = ProjectStatus.ACTIVE;
        synchronizeLifecycle(today, now);
    }
}
