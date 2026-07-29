package com.teample.controller;

import com.teample.dto.MinutesRequest;
import com.teample.dto.MinutesResponse;
import com.teample.dto.MinutesSummary;
import com.teample.service.MinutesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/minutes")
@RequiredArgsConstructor
public class MinutesController {

    private final MinutesService minutesService;

    @GetMapping
    public ResponseEntity<List<MinutesSummary>> listMinutes(@PathVariable String projectId) {
        return ResponseEntity.ok(minutesService.findByProjectId(projectId));
    }

    @PostMapping
    public ResponseEntity<MinutesResponse> createMinutes(
            @PathVariable String projectId,
            @Valid @RequestBody MinutesRequest request) {
        return ResponseEntity.ok(minutesService.create(projectId, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MinutesResponse> getMinutes(
            @PathVariable String projectId,
            @PathVariable String id) {
        return minutesService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<MinutesResponse> updateMinutes(
            @PathVariable String projectId,
            @PathVariable String id,
            @RequestBody MinutesResponse request) {
        return minutesService.update(id, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
