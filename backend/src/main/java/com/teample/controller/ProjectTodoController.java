package com.teample.controller;

import com.teample.dto.ProjectTodoResponse;
import com.teample.dto.TodoReorderRequest;
import com.teample.dto.TodoStatusRequest;
import com.teample.entity.TodoStatus;
import com.teample.security.AuthenticatedUser;
import com.teample.security.SupabaseAuthenticationFilter;
import com.teample.service.ProjectMemberService;
import com.teample.service.ProjectTodoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/todos")
@RequiredArgsConstructor
public class ProjectTodoController {

    private final ProjectTodoService todoService;
    private final ProjectMemberService projectMemberService;

    @GetMapping
    public ResponseEntity<List<ProjectTodoResponse>> listTodos(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "TODO") TodoStatus status,
            HttpServletRequest request
    ) {
        projectMemberService.ensureProjectMember(projectId, authenticatedUser(request), isAdmin(request));
        return ResponseEntity.ok(todoService.findByProject(projectId, status));
    }

    @PatchMapping("/{todoId}/status")
    public ResponseEntity<ProjectTodoResponse> updateStatus(
            @PathVariable String projectId,
            @PathVariable String todoId,
            @Valid @RequestBody TodoStatusRequest request,
            HttpServletRequest servletRequest
    ) {
        projectMemberService.ensureProjectMember(projectId, authenticatedUser(servletRequest), isAdmin(servletRequest));
        return ResponseEntity.ok(todoService.updateStatus(projectId, todoId, request.getStatus()));
    }

    @PutMapping("/order")
    public ResponseEntity<List<ProjectTodoResponse>> reorder(
            @PathVariable String projectId,
            @Valid @RequestBody TodoReorderRequest request,
            HttpServletRequest servletRequest
    ) {
        projectMemberService.ensureProjectMember(projectId, authenticatedUser(servletRequest), isAdmin(servletRequest));
        return ResponseEntity.ok(todoService.reorder(projectId, request.getOrderedTodoIds()));
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
