package com.teample.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.time.LocalDate;
import com.teample.entity.ProjectStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {
    private String id;
    private String name;
    private List<String> members;
    private String createdAt;
    private LocalDate disposalDeadline;
    private ProjectStatus status;
    private String disposedAt;
}
