package com.teample.controller;

import com.teample.dto.ProjectTodoResponse;
import com.teample.dto.TodoReorderRequest;
import com.teample.dto.TodoStatusRequest;
import com.teample.entity.TodoStatus;
import com.teample.service.ProjectTodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/todos")
@RequiredArgsConstructor
public class ProjectTodoController {

    private final ProjectTodoService todoService;

    @GetMapping
    public ResponseEntity<List<ProjectTodoResponse>> listTodos(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "TODO") TodoStatus status) {
        return ResponseEntity.ok(todoService.findByProject(projectId, status));
    }

    @PatchMapping("/{todoId}/status")
    public ResponseEntity<ProjectTodoResponse> updateStatus(
            @PathVariable String projectId,
            @PathVariable String todoId,
            @Valid @RequestBody TodoStatusRequest request) {
        return ResponseEntity.ok(todoService.updateStatus(projectId, todoId, request.getStatus()));
    }

    @PutMapping("/order")
    public ResponseEntity<List<ProjectTodoResponse>> reorder(
            @PathVariable String projectId,
            @Valid @RequestBody TodoReorderRequest request) {
        return ResponseEntity.ok(todoService.reorder(projectId, request.getOrderedTodoIds()));
    }
}
