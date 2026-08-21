package com.teample.controller;

import com.teample.dto.dashboard.DashboardProjectResponse;
import com.teample.dto.dashboard.MyProjectDashboardResponse;
import com.teample.dto.dashboard.ProjectDashboardResponse;
import com.teample.dto.dashboard.TeamProjectDashboardResponse;
import com.teample.dto.dashboard.TodoAssignmentResponse;
import com.teample.dto.dashboard.TodoProgressUpdateRequest;
import com.teample.exception.TodoAccessDeniedException;
import com.teample.security.AuthenticatedUser;
import com.teample.security.SupabaseAuthenticationFilter;
import com.teample.service.DashboardService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard/projects")
    public ResponseEntity<List<DashboardProjectResponse>> getProjectDashboards(HttpServletRequest request) {
        return ResponseEntity.ok(dashboardService.findLegacyProjectDashboards(authenticatedUser(request), isAdmin(request)));
    }

    @GetMapping("/dashboard/projects/my")
    public ResponseEntity<List<MyProjectDashboardResponse>> getMyProjectDashboards(HttpServletRequest request) {
        return ResponseEntity.ok(dashboardService.findMyProjectDashboards(authenticatedUser(request), isAdmin(request)));
    }

    @GetMapping("/projects/{projectId}/dashboard")
    public ResponseEntity<ProjectDashboardResponse> getProjectDashboard(
            @PathVariable String projectId,
            HttpServletRequest request) {
        return dashboardService.findLegacyProjectDashboard(projectId, authenticatedUser(request), isAdmin(request))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/projects/{projectId}/dashboard/my")
    public ResponseEntity<MyProjectDashboardResponse> getMyProjectDashboard(
            @PathVariable String projectId,
            HttpServletRequest request) {
        return dashboardService.findMyProjectDashboard(projectId, authenticatedUser(request), isAdmin(request))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/projects/{projectId}/dashboard/team")
    public ResponseEntity<TeamProjectDashboardResponse> getTeamProjectDashboard(
            @PathVariable String projectId,
            HttpServletRequest request) {
        return dashboardService.findTeamProjectDashboard(projectId, authenticatedUser(request), isAdmin(request))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/todo-assignments/{assignmentId}")
    public ResponseEntity<TodoAssignmentResponse> updateTodoAssignmentProgress(
            @PathVariable String assignmentId,
            @RequestBody TodoProgressUpdateRequest request,
            HttpServletRequest servletRequest) {
        try {
            return dashboardService.updateProgressByAssignmentId(
                            assignmentId, authenticatedUser(servletRequest), isAdmin(servletRequest), request)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (TodoAccessDeniedException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PatchMapping("/todos/{todoId}/progress")
    public ResponseEntity<TodoAssignmentResponse> updateMyTodoProgress(
            @PathVariable String todoId,
            @RequestBody TodoProgressUpdateRequest request,
            HttpServletRequest servletRequest) {
        try {
            return dashboardService.updateProgressByTodoAndUser(
                            todoId, authenticatedUser(servletRequest), isAdmin(servletRequest), request)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (TodoAccessDeniedException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @ExceptionHandler(TodoAccessDeniedException.class)
    public ResponseEntity<Void> handleTodoAccessDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
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
