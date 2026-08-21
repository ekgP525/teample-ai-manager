package com.teample.controller;

import com.teample.dto.ProjectRequest;
import com.teample.dto.ProjectResponse;
import com.teample.dto.project.ProjectMemberResponse;
import com.teample.security.AuthenticatedUser;
import com.teample.security.SupabaseAuthenticationFilter;
import com.teample.service.ProjectMemberService;
import com.teample.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMemberService projectMemberService;

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> listProjects(HttpServletRequest request) {
        return ResponseEntity.ok(projectService.findAll(authenticatedUser(request), isAdmin(request)));
    }

    @GetMapping("/trash")
    public ResponseEntity<List<ProjectResponse>> listDeletedProjects(HttpServletRequest request) {
        return ResponseEntity.ok(projectService.findTrash(authenticatedUser(request), isAdmin(request)));
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody ProjectRequest request,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(projectService.create(request, authenticatedUser(servletRequest)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProject(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        projectMemberService.ensureProjectMember(id, authenticatedUser(request), isAdmin(request));
        return projectService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<ProjectMemberResponse>> getProjectMembers(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        return projectMemberService.findProjectMembers(id, authenticatedUser(request), isAdmin(request))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        projectMemberService.ensureProjectOwner(id, authenticatedUser(request), isAdmin(request));
        if (projectService.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PatchMapping("/{id}/restore")
    public ResponseEntity<ProjectResponse> restoreProject(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        projectMemberService.ensureProjectOwner(id, authenticatedUser(request), isAdmin(request));
        return projectService.restore(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> permanentlyDeleteProject(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        projectMemberService.ensureProjectOwner(id, authenticatedUser(request), isAdmin(request));
        if (projectService.permanentlyDelete(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(ProjectMemberService.ProjectMemberAccessDeniedException.class)
    public ResponseEntity<Void> handleProjectMemberAccessDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler(ProjectMemberService.ProjectNotFoundException.class)
    public ResponseEntity<Void> handleProjectNotFound() {
        return ResponseEntity.notFound().build();
    }

    private AuthenticatedUser authenticatedUser(HttpServletRequest request) {
        Object value = request.getAttribute(SupabaseAuthenticationFilter.AUTHENTICATED_USER_ATTRIBUTE);
        if (value instanceof AuthenticatedUser user) {
            return user;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user is not resolved.");
    }

    private boolean isAdmin(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(SupabaseAuthenticationFilter.ADMIN_TEST_USER_ATTRIBUTE));
    }
}
