package com.teample.service;

import com.teample.dto.ProjectRequest;
import com.teample.dto.ProjectResponse;
import com.teample.entity.Project;
import com.teample.repository.MinutesRepository;
import com.teample.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final MinutesRepository minutesRepository;
    private final TodoProgressSyncService todoProgressSyncService;

    public ProjectResponse create(ProjectRequest request) {
        Project project = Project.builder()
                .name(request.getName())
                .members(request.getMembers())
                .build();
        Project saved = projectRepository.save(project);
        return toResponse(saved);
    }

    public List<ProjectResponse> findAll() {
        return projectRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<ProjectResponse> findById(String id) {
        return projectRepository.findById(id).map(this::toResponse);
    }

    @Transactional
    public boolean delete(String id) {
        return projectRepository.findById(id).map(project -> {
            todoProgressSyncService.deleteByProject(project);
            minutesRepository.deleteAll(minutesRepository.findByProjectIdOrderByCreatedAtDesc(id));
            projectRepository.delete(project);
            return true;
        }).orElse(false);
    }

    private ProjectResponse toResponse(Project p) {
        return ProjectResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .members(p.getMembers())
                .createdAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : "")
                .build();
    }
}