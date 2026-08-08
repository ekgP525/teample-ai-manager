package com.teample.controller;

import com.teample.dto.dashboard.DashboardProjectResponse;
import com.teample.dto.dashboard.ProjectDashboardResponse;
import com.teample.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public ResponseEntity<List<DashboardProjectResponse>> getMyProjectDashboards(
            @RequestParam(required = false) String memberName) {
        return ResponseEntity.ok(dashboardService.findMyProjectDashboards(memberName));
    }

    @GetMapping("/projects/{projectId}/dashboard")
    public ResponseEntity<ProjectDashboardResponse> getProjectDashboard(
            @PathVariable String projectId,
            @RequestParam(required = false) String memberName) {
        return dashboardService.findProjectDashboard(projectId, memberName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
