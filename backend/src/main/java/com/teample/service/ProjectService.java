package com.teample.service;

import com.teample.dto.ProjectRequest;
import com.teample.dto.ProjectResponse;
import com.teample.entity.Project;
import com.teample.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    private ProjectResponse toResponse(Project p) {
        return ProjectResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .members(p.getMembers())
                .createdAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : "")
                .build();
    }
}
