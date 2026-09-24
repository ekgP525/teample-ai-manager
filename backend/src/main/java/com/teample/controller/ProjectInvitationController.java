package com.teample.controller;

import com.teample.dto.project.JoinProjectInvitationRequest;
import com.teample.dto.project.JoinProjectInvitationResponse;
import com.teample.dto.project.ProjectInvitationResponse;
import com.teample.security.AuthenticatedUser;
import com.teample.security.SupabaseAuthenticationFilter;
import com.teample.service.ProjectInvitationService;
import com.teample.service.ProjectMemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class ProjectInvitationController {

    private final ProjectInvitationService invitationService;

    @org.springframework.web.bind.annotation.GetMapping("/api/projects/{projectId}/invitations/active")
    public ResponseEntity<ProjectInvitationResponse> activeInvitation(@PathVariable String projectId, HttpServletRequest request) {
        return invitationService.findActive(projectId, authenticatedUser(request), isAdmin(request))
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/api/projects/{projectId}/invitations")
    public ResponseEntity<ProjectInvitationResponse> createInvitation(
            @PathVariable String projectId, HttpServletRequest request) {
        return ResponseEntity.ok(invitationService.create(projectId, authenticatedUser(request), isAdmin(request)));
    }

    @PostMapping("/api/project-invitations/join")
    public ResponseEntity<JoinProjectInvitationResponse> joinInvitation(
            @Valid @RequestBody JoinProjectInvitationRequest request, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(invitationService.join(request.code(), authenticatedUser(servletRequest)));
    }

    @ExceptionHandler(ProjectInvitationService.InvitationNotFoundException.class)
    public ResponseEntity<Void> handleNotFound() { return ResponseEntity.notFound().build(); }

    @ExceptionHandler(ProjectInvitationService.InvitationExpiredException.class)
    public ResponseEntity<Void> handleExpired() { return ResponseEntity.status(HttpStatus.GONE).build(); }

    @ExceptionHandler(ProjectInvitationService.AlreadyProjectMemberException.class)
    public ResponseEntity<Void> handleAlreadyMember() { return ResponseEntity.status(HttpStatus.CONFLICT).build(); }

    @ExceptionHandler(ProjectMemberService.ProjectMemberAccessDeniedException.class)
    public ResponseEntity<Void> handleAccessDenied() { return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); }

    @ExceptionHandler(ProjectMemberService.ProjectNotFoundException.class)
    public ResponseEntity<Void> handleProjectNotFound() { return ResponseEntity.notFound().build(); }

    private AuthenticatedUser authenticatedUser(HttpServletRequest request) {
        Object value = request.getAttribute(SupabaseAuthenticationFilter.AUTHENTICATED_USER_ATTRIBUTE);
        if (value instanceof AuthenticatedUser user) return user;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user is not resolved.");
    }

    private boolean isAdmin(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(SupabaseAuthenticationFilter.ADMIN_TEST_USER_ATTRIBUTE));
    }
}
