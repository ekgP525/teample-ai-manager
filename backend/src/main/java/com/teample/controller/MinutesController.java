package com.teample.controller;

import com.teample.dto.MinutesRequest;
import com.teample.dto.MinutesResponse;
import com.teample.dto.MinutesSummary;
import com.teample.security.AuthenticatedUser;
import com.teample.security.SupabaseAuthenticationFilter;
import com.teample.service.MinutesService;
import com.teample.service.ProjectMemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/minutes")
@RequiredArgsConstructor
public class MinutesController {

    private final MinutesService minutesService;
    private final com.teample.service.MinutesGenerationService generationService;
    private final ProjectMemberService projectMemberService;

    @GetMapping
    public ResponseEntity<List<MinutesSummary>> listMinutes(
            @PathVariable String projectId,
            HttpServletRequest request
    ) {
        projectMemberService.ensureProjectMember(projectId, authenticatedUser(request), isAdmin(request));
        return ResponseEntity.ok(minutesService.findByProjectId(projectId));
    }

    @PostMapping
    public ResponseEntity<MinutesResponse> createMinutes(
            @PathVariable String projectId,
            @Valid @RequestBody MinutesRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest servletRequest
    ) {
        projectMemberService.ensureProjectMember(projectId, authenticatedUser(servletRequest), isAdmin(servletRequest));
        return ResponseEntity.ok(generationService.create(projectId, request, authenticatedUser(servletRequest), isAdmin(servletRequest), key));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MinutesResponse> getMinutes(
            @PathVariable String projectId,
            @PathVariable String id,
            HttpServletRequest request
    ) {
        projectMemberService.ensureProjectMember(projectId, authenticatedUser(request), isAdmin(request));
        return minutesService.findByProjectIdAndId(projectId, id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<MinutesResponse> updateMinutes(
            @PathVariable String projectId,
            @PathVariable String id,
            @Valid @RequestBody MinutesResponse request,
            HttpServletRequest servletRequest
    ) {
        projectMemberService.ensureProjectMember(projectId, authenticatedUser(servletRequest), isAdmin(servletRequest));
        return minutesService.update(projectId, id, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMinutes(
            @PathVariable String projectId,
            @PathVariable String id,
            HttpServletRequest request
    ) {
        projectMemberService.ensureProjectMember(projectId, authenticatedUser(request), isAdmin(request));
        if (minutesService.delete(projectId, id)) {
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
