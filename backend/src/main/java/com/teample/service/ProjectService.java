package com.teample.service;

import com.teample.dto.ProjectRequest;
import com.teample.dto.ProjectResponse;
import com.teample.entity.Project;
import com.teample.entity.ProjectStatus;
import com.teample.repository.IntegratedTodoRepository;
import com.teample.repository.MinutesRepository;
import com.teample.repository.ProjectMemberRepository;
import com.teample.repository.ProjectRepository;
import com.teample.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final IntegratedTodoRepository integratedTodoRepository;
    private final MinutesRepository minutesRepository;
    private final TodoProgressSyncService todoProgressSyncService;
    private final ProjectMemberService projectMemberService;
    private final ProjectMemberRepository projectMemberRepository;

    public ProjectResponse create(ProjectRequest request) {
        return create(request, null);
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request, AuthenticatedUser owner) {
        Project project = Project.builder()
                .name(request.getName())
                .members(request.getMembers())
                .endDate(request.getEndDate())
                .build();
        synchronizeStatus(project);
        Project saved = projectRepository.save(project);
        projectMemberService.addOwner(saved, owner);
        return ProjectResponse.from(saved);
    }

    @Transactional
    public List<ProjectResponse> findAll() {
        return projectRepository.findAll().stream()
                .peek(this::synchronizeStatus)
                .filter(Project::isVisibleInActiveList)
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional
    public List<ProjectResponse> findAll(AuthenticatedUser user, boolean admin) {
        return projectRepository.findAll().stream()
                .peek(this::synchronizeStatus)
                .filter(Project::isVisibleInActiveList)
                .filter(project -> projectMemberService.canAccessProject(project, user, admin))
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional
    public List<ProjectResponse> findTrash() {
        return projectRepository.findByStatus(ProjectStatus.DELETED).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional
    public List<ProjectResponse> findTrash(AuthenticatedUser user, boolean admin) {
        return projectRepository.findByStatus(ProjectStatus.DELETED).stream()
                .filter(project -> projectMemberService.canAccessProject(project, user, admin))
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional
    public int synchronizeExpiredProjects() {
        LocalDate today = LocalDate.now();
        return (int) projectRepository.findAll().stream()
                .filter(Project::isVisibleInActiveList)
                .filter(project -> project.hasEndDatePassed(today))
                .peek(this::synchronizeStatus)
                .filter(project -> project.getResolvedStatus().isEnded())
                .count();
    }

    @Transactional
    public Optional<ProjectResponse> findById(String id) {
        return projectRepository.findById(id).map(project -> {
            synchronizeStatus(project);
            return ProjectResponse.from(project);
        });
    }

    @Transactional
    public boolean delete(String id) {
        return projectRepository.findById(id).map(project -> {
            project.markDeleted(LocalDateTime.now());
            return true;
        }).orElse(false);
    }

    @Transactional
    public Optional<ProjectResponse> restore(String id) {
        return projectRepository.findById(id)
                .filter(Project::isDeleted)
                .map(project -> {
                    project.restore(LocalDate.now(), LocalDateTime.now());
                    return ProjectResponse.from(project);
                });
    }

    @Transactional
    public boolean permanentlyDelete(String id) {
        return projectRepository.findById(id).map(project -> {
            projectMemberRepository.deleteByProjectId(project.getId());
            todoProgressSyncService.deleteByProject(project);
            integratedTodoRepository.deleteByProjectId(project.getId());
            minutesRepository.deleteByProjectId(project.getId());
            projectRepository.delete(project);
            return true;
        }).orElse(false);
    }

    private void synchronizeStatus(Project project) {
        project.synchronizeLifecycle(LocalDate.now(), LocalDateTime.now());
    }
}