package com.teample.service;

import com.teample.dto.project.ProjectMemberResponse;
import com.teample.entity.Project;
import com.teample.entity.ProjectMember;
import com.teample.entity.ProjectMemberRole;
import com.teample.repository.ProjectMemberRepository;
import com.teample.repository.ProjectRepository;
import com.teample.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;

    @Transactional
    public void addOwner(Project project, AuthenticatedUser user) {
        if (project == null || user == null) {
            return;
        }
        if (projectMemberRepository.existsByProjectIdAndUserId(project.getId(), user.authUserId())) {
            return;
        }
        projectMemberRepository.save(ProjectMember.builder()
                .project(project)
                .userId(user.authUserId())
                .displayName(resolveDisplayName(user))
                .role(ProjectMemberRole.OWNER)
                .build());
    }

    @Transactional(readOnly = true)
    public Optional<List<ProjectMemberResponse>> findProjectMembers(
            String projectId,
            AuthenticatedUser currentUser,
            boolean admin
    ) {
        Project project = projectRepository.findById(projectId)
                .orElse(null);
        if (project == null) {
            return Optional.empty();
        }
        ensureProjectMember(project, currentUser, admin);
        return Optional.of(projectMemberRepository.findByProjectIdOrderByJoinedAtAsc(projectId).stream()
                .map(ProjectMemberResponse::from)
                .toList());
    }

    @Transactional(readOnly = true)
    public void ensureProjectMember(String projectId, AuthenticatedUser user, boolean admin) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found."));
        ensureProjectMember(project, user, admin);
    }

    @Transactional(readOnly = true)
    public void ensureProjectMember(Project project, AuthenticatedUser user, boolean admin) {
        if (canAccessProject(project, user, admin)) {
            return;
        }
        throw new ProjectMemberAccessDeniedException("Only project members can access this project.");
    }

    @Transactional(readOnly = true)
    public boolean canAccessProject(Project project, AuthenticatedUser user, boolean admin) {
        return admin || isProjectMember(project, user);
    }

    @Transactional(readOnly = true)
    public void ensureProjectOwner(String projectId, AuthenticatedUser user, boolean admin) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found."));
        ensureProjectOwner(project, user, admin);
    }

    @Transactional(readOnly = true)
    public void ensureProjectOwner(Project project, AuthenticatedUser user, boolean admin) {
        if (admin) {
            return;
        }
        if (user == null || project == null || project.getId() == null) {
            throw new ProjectMemberAccessDeniedException("Only project owners can manage this project.");
        }
        if (projectMemberRepository.existsByProjectIdAndUserIdAndRole(
                project.getId(), user.authUserId(), ProjectMemberRole.OWNER)) {
            return;
        }
        if (!projectMemberRepository.existsByProjectId(project.getId()) && isLegacyProjectMember(project, user)) {
            return;
        }
        throw new ProjectMemberAccessDeniedException("Only project owners can manage this project.");
    }

    @Transactional(readOnly = true)
    public boolean isProjectMember(String projectId, AuthenticatedUser user) {
        if (user == null) {
            return false;
        }
        return projectRepository.findById(projectId)
                .map(project -> isProjectMember(project, user))
                .orElse(false);
    }

    private boolean isProjectMember(Project project, AuthenticatedUser user) {
        if (user == null || project == null || project.getId() == null) {
            return false;
        }
        if (projectMemberRepository.existsByProjectIdAndUserId(project.getId(), user.authUserId())) {
            return true;
        }
        return !projectMemberRepository.existsByProjectId(project.getId()) && isLegacyProjectMember(project, user);
    }

    private boolean isLegacyProjectMember(Project project, AuthenticatedUser user) {
        if (project.getMembers() == null || project.getMembers().isEmpty()) {
            return false;
        }
        List<String> candidates = Stream.of(user.authUserId(), user.memberKey(), user.email())
                .map(this::normalize)
                .filter(value -> value != null)
                .toList();
        if (candidates.isEmpty()) {
            return false;
        }
        return project.getMembers().stream()
                .map(this::normalize)
                .anyMatch(member -> member != null && candidates.stream().anyMatch(member::equalsIgnoreCase));
    }

    private String resolveDisplayName(AuthenticatedUser user) {
        if (user.memberKey() != null && !user.memberKey().isBlank()) {
            return user.memberKey().trim();
        }
        if (user.email() != null && !user.email().isBlank()) {
            return user.email().trim();
        }
        return user.authUserId();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static class ProjectMemberAccessDeniedException extends RuntimeException {
        public ProjectMemberAccessDeniedException(String message) {
            super(message);
        }
    }

    public static class ProjectNotFoundException extends RuntimeException {
        public ProjectNotFoundException(String message) {
            super(message);
        }
    }
}
