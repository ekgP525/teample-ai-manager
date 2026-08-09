package com.teample.controller;

import com.teample.dto.dashboard.DashboardProjectResponse;
import com.teample.dto.dashboard.MyProjectDashboardResponse;
import com.teample.dto.dashboard.ProjectDashboardResponse;
import com.teample.dto.dashboard.TeamProjectDashboardResponse;
import com.teample.dto.dashboard.TodoAssignmentResponse;
import com.teample.dto.dashboard.TodoProgressUpdateRequest;
import com.teample.exception.TodoAccessDeniedException;
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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class DashboardController {

    private static final String CURRENT_USER_ID_ATTRIBUTE = "currentUserId";
    private static final String CURRENT_USER_ID_HEADER = "X-Current-User-Id";

    private final DashboardService dashboardService;

    @GetMapping("/dashboard/projects")
    public ResponseEntity<List<DashboardProjectResponse>> getProjectDashboards(HttpServletRequest request) {
        String currentUserId = currentUserId(request);
        return ResponseEntity.ok(dashboardService.findLegacyProjectDashboards(currentUserId));
    }

    @GetMapping("/dashboard/projects/my")
    public ResponseEntity<List<MyProjectDashboardResponse>> getMyProjectDashboards(HttpServletRequest request) {
        String currentUserId = currentUserId(request);
        return ResponseEntity.ok(dashboardService.findMyProjectDashboards(currentUserId));
    }

    @GetMapping("/projects/{projectId}/dashboard")
    public ResponseEntity<ProjectDashboardResponse> getProjectDashboard(
            @PathVariable String projectId,
            HttpServletRequest request) {
        String currentUserId = currentUserId(request);
        return dashboardService.findLegacyProjectDashboard(projectId, currentUserId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/projects/{projectId}/dashboard/my")
    public ResponseEntity<MyProjectDashboardResponse> getMyProjectDashboard(
            @PathVariable String projectId,
            HttpServletRequest request) {
        String currentUserId = currentUserId(request);
        return dashboardService.findMyProjectDashboard(projectId, currentUserId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/projects/{projectId}/dashboard/team")
    public ResponseEntity<TeamProjectDashboardResponse> getTeamProjectDashboard(
            @PathVariable String projectId,
            HttpServletRequest request) {
        currentUserId(request);
        return dashboardService.findTeamProjectDashboard(projectId, currentUserId(request))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/todo-assignments/{assignmentId}")
    public ResponseEntity<TodoAssignmentResponse> updateTodoAssignmentProgress(
            @PathVariable String assignmentId,
            @RequestBody TodoProgressUpdateRequest request,
            HttpServletRequest servletRequest) {
        String currentUserId = currentUserId(servletRequest);
        try {
            return dashboardService.updateProgressByAssignmentId(assignmentId, currentUserId, request)
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
        String currentUserId = currentUserId(servletRequest);
        try {
            return dashboardService.updateProgressByTodoAndUser(todoId, currentUserId, request)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @ExceptionHandler(TodoAccessDeniedException.class)
    public ResponseEntity<Void> handleTodoAccessDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    private String currentUserId(HttpServletRequest request) {
        Object value = request.getAttribute(CURRENT_USER_ID_ATTRIBUTE);
        String currentUserId = value != null ? value.toString() : request.getHeader(CURRENT_USER_ID_HEADER);
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user is not resolved.");
        }
        return URLDecoder.decode(currentUserId.trim(), StandardCharsets.UTF_8);
    }
}