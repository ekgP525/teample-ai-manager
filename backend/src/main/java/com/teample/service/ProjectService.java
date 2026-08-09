package com.teample.service;

import com.teample.dto.ProjectRequest;
import com.teample.dto.ProjectResponse;
import com.teample.entity.Project;
import com.teample.repository.ProjectRepository;
import com.teample.entity.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;

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

    private ProjectResponse toResponse(Project p) {
        return ProjectResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .members(p.getMembers() != null ? p.getMembers() : Collections.emptyList())
                .createdAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : "")
                .disposalDeadline(p.getDisposalDeadline())
                .status(p.getStatus() != null ? p.getStatus() : ProjectStatus.ACTIVE)
                .disposedAt(p.getDisposedAt() != null ? p.getDisposedAt().toString() : null)
                .build();
    }
}
