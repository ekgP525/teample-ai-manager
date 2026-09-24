package com.teample.service;

import com.teample.dto.ProjectRequest;
import com.teample.dto.ProjectResponse;
import com.teample.entity.Project;
import com.teample.entity.ProjectMember;
import com.teample.entity.ProjectStatus;
import com.teample.repository.IntegratedTodoRepository;
import com.teample.repository.MinutesRepository;
import com.teample.repository.ProjectInvitationRepository;
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
    private final ProjectInvitationRepository projectInvitationRepository;

    public ProjectResponse create(ProjectRequest request) {
        return create(request, null);
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request, AuthenticatedUser owner) {
        Project project = Project.builder()
                .name(request.getName())
                .members(List.of())
                .endDate(request.getEndDate())
                .build();
        synchronizeStatus(project);
        Project saved = projectRepository.save(project);
        projectMemberService.addOwner(saved, owner);
        return ProjectResponse.from(saved, resolveProjectMemberNames(saved, owner));
    }

    @Transactional
    public List<ProjectResponse> findAll() {
        return projectRepository.findAll().stream()
                .peek(this::synchronizeStatus)
                .filter(Project::isVisibleInActiveList)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<ProjectResponse> findAll(AuthenticatedUser user, boolean admin) {
        return projectRepository.findAll().stream()
                .peek(this::synchronizeStatus)
                .filter(Project::isVisibleInActiveList)
                .filter(project -> projectMemberService.canAccessProject(project, user, admin))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<ProjectResponse> findTrash() {
        return projectRepository.findByStatus(ProjectStatus.DELETED).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<ProjectResponse> findTrash(AuthenticatedUser user, boolean admin) {
        return projectRepository.findByStatus(ProjectStatus.DELETED).stream()
                .filter(project -> projectMemberService.canAccessProject(project, user, admin))
                .map(this::toResponse)
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
            return toResponse(project);
        });
    }

    @Transactional
    public boolean delete(String id) {
        return projectRepository.findById(id).map(project -> {
            project.markDeleted(LocalDateTime.now());
            projectInvitationRepository.deactivateByProjectId(id);
            return true;
        }).orElse(false);
    }

    @Transactional
    public Optional<ProjectResponse> restore(String id) {
        return projectRepository.findById(id)
                .filter(Project::isDeleted)
                .map(project -> {
                    project.restore(LocalDate.now(), LocalDateTime.now());
                    return toResponse(project);
                });
    }

    @Transactional
    public boolean permanentlyDelete(String id) {
        return projectRepository.findById(id).map(project -> {
            projectInvitationRepository.deleteByProjectId(project.getId());
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

    private ProjectResponse toResponse(Project project) {
        return ProjectResponse.from(project, resolveProjectMemberNames(project, null));
    }

    private List<String> resolveProjectMemberNames(Project project, AuthenticatedUser fallbackOwner) {
        List<String> accountMemberNames = projectMemberRepository.findByProjectIdOrderByJoinedAtAsc(project.getId()).stream()
                .map(ProjectMember::getDisplayName)
                .map(this::normalizeOptional)
                .filter(value -> value != null)
                .distinct()
                .toList();
        if (!accountMemberNames.isEmpty()) {
            return accountMemberNames;
        }
        if (fallbackOwner != null) {
            String ownerName = normalizeOptional(fallbackOwner.memberKey());
            if (ownerName != null) {
                return List.of(ownerName);
            }
        }
        return project.getMembers() != null ? project.getMembers() : List.of();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
