package com.teample.dto;

import com.teample.entity.Project;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {
    private String id;
    private String name;
    private List<String> members;
    private String createdAt;
    private LocalDate endDate;
    private LocalDate disposalDeadline;
    private String status;
    private String endedAt;
    private String disposedAt;
    private String deletedAt;

    public static ProjectResponse from(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .members(project.getMembers() != null ? project.getMembers() : Collections.emptyList())
                .createdAt(format(project.getCreatedAt()))
                .endDate(project.getEndDate())
                .disposalDeadline(project.getEndDate())
                .status(project.getResolvedStatus().toClientStatus())
                .endedAt(format(project.getEndedAt()))
                .disposedAt(format(project.getEndedAt()))
                .deletedAt(format(project.getDeletedAt()))
                .build();
    }

    private static String format(LocalDateTime value) {
        return value != null ? value.toString() : null;
    }
}
