package com.teample.controller;

import com.teample.dto.dashboard.DashboardProjectResponse;
import com.teample.dto.dashboard.MyProjectDashboardResponse;
import com.teample.dto.dashboard.ProjectDashboardResponse;
import com.teample.dto.dashboard.TeamProjectDashboardResponse;
import com.teample.dto.dashboard.TodoAssignmentResponse;
import com.teample.dto.dashboard.TodoProgressUpdateRequest;
import com.teample.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard/projects")
    public ResponseEntity<List<DashboardProjectResponse>> getProjectDashboards(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String memberName) {
        return ResponseEntity.ok(dashboardService.findLegacyProjectDashboards(resolveUserId(userId, memberName)));
    }

    @GetMapping("/dashboard/projects/my")
    public ResponseEntity<List<MyProjectDashboardResponse>> getMyProjectDashboards(@RequestParam String userId) {
        return ResponseEntity.ok(dashboardService.findMyProjectDashboards(userId));
    }

    @GetMapping("/projects/{projectId}/dashboard")
    public ResponseEntity<ProjectDashboardResponse> getProjectDashboard(
            @PathVariable String projectId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String memberName) {
        return dashboardService.findLegacyProjectDashboard(projectId, resolveUserId(userId, memberName))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/projects/{projectId}/dashboard/my")
    public ResponseEntity<MyProjectDashboardResponse> getMyProjectDashboard(
            @PathVariable String projectId,
            @RequestParam String userId) {
        return dashboardService.findMyProjectDashboard(projectId, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/projects/{projectId}/dashboard/team")
    public ResponseEntity<TeamProjectDashboardResponse> getTeamProjectDashboard(@PathVariable String projectId) {
        return dashboardService.findTeamProjectDashboard(projectId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/todo-assignments/{assignmentId}")
    public ResponseEntity<TodoAssignmentResponse> updateTodoAssignmentProgress(
            @PathVariable String assignmentId,
            @RequestBody TodoProgressUpdateRequest request) {
        try {
            return dashboardService.updateProgressByAssignmentId(assignmentId, request)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/todos/{todoId}/progress")
    public ResponseEntity<TodoAssignmentResponse> updateMyTodoProgress(
            @PathVariable String todoId,
            @RequestParam String userId,
            @RequestBody TodoProgressUpdateRequest request) {
        try {
            return dashboardService.updateProgressByTodoAndUser(todoId, userId, request)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private String resolveUserId(String userId, String memberName) {
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        return memberName;
    }
}