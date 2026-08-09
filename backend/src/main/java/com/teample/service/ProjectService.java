package com.teample.service;

import com.teample.dto.ProjectRequest;
import com.teample.dto.ProjectResponse;
import com.teample.entity.Project;
import com.teample.entity.ProjectStatus;
import com.teample.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectResponse create(ProjectRequest request) {
        Project project = Project.builder()
                .name(request.getName())
                .members(request.getMembers())
                .disposalDeadline(request.getDisposalDeadline())
                .build();
        synchronizeStatus(project);
        Project saved = projectRepository.save(project);
        return toResponse(saved);
    }

    @Transactional
    public List<ProjectResponse> findAll() {
        return projectRepository.findAll().stream()
                .peek(this::synchronizeStatus)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public Optional<ProjectResponse> findById(String id) {
        return projectRepository.findById(id).map(project -> {
            synchronizeStatus(project);
            return toResponse(project);
        });
    }

    @Transactional
    public boolean delete(String id) {
        return projectRepository.findById(id).map(project -> {
            project.setStatus(ProjectStatus.DISPOSED);
            project.setDisposedAt(LocalDateTime.now());
            return true;
        }).orElse(false);
    }

    private void synchronizeStatus(Project project) {
        if (project.getStatus() == ProjectStatus.DISPOSED) {
            return;
        }
        LocalDate deadline = project.getDisposalDeadline();
        if (deadline == null) {
            project.setStatus(ProjectStatus.ACTIVE);
        } else if (deadline.isBefore(LocalDate.now())) {
            project.setStatus(ProjectStatus.DISPOSED);
            if (project.getDisposedAt() == null) {
                project.setDisposedAt(LocalDateTime.now());
            }
        } else {
            project.setStatus(ProjectStatus.DISPOSAL_SCHEDULED);
        }
    }

    private ProjectResponse toResponse(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .members(project.getMembers() != null ? project.getMembers() : Collections.emptyList())
                .createdAt(project.getCreatedAt() != null ? project.getCreatedAt().toString() : "")
                .disposalDeadline(project.getDisposalDeadline())
                .status(project.getStatus() != null ? project.getStatus() : ProjectStatus.ACTIVE)
                .disposedAt(project.getDisposedAt() != null ? project.getDisposedAt().toString() : null)
                .build();
    }
}
