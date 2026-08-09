package com.teample.service;

import com.teample.dto.ProjectTodoResponse;
import com.teample.dto.TodoAssigneeResponse;
import com.teample.entity.*;
import com.teample.repository.MinutesRepository;
import com.teample.repository.ProjectRepository;
import com.teample.repository.ProjectTodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectTodoService {

    private final ProjectTodoRepository todoRepository;
    private final ProjectRepository projectRepository;
    private final MinutesRepository minutesRepository;

    @Transactional
    public List<ProjectTodoResponse> findByProject(String projectId, TodoStatus status) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        TodoStatus requestedStatus = status != null ? status : TodoStatus.TODO;
        if (requestedStatus == TodoStatus.TODO) {
            minutesRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                    .forEach(this::synchronizeFromMinutes);
        }

        return todoRepository
                .findByProjectIdAndStatusOrderByPriorityOrderAscCreatedAtAsc(projectId, requestedStatus)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void synchronizeFromMinutes(Minutes minutes) {
        if (minutes == null || minutes.getId() == null || minutes.getProject() == null) {
            return;
        }

        List<TodoData> sourceTodos = minutes.getTodos() != null
                ? minutes.getTodos() : Collections.emptyList();
        Map<Integer, ProjectTodo> existingByIndex = todoRepository
                .findByMinutesIdOrderBySourceIndexAsc(minutes.getId())
                .stream()
                .filter(todo -> todo.getSourceIndex() != null)
                .collect(Collectors.toMap(ProjectTodo::getSourceIndex, Function.identity(), (first, ignored) -> first));

        int nextPriority = todoRepository.findMaxPriorityOrderByProjectId(minutes.getProject().getId());
        List<ProjectTodo> changed = new ArrayList<>();

        for (int index = 0; index < sourceTodos.size(); index++) {
            TodoData source = sourceTodos.get(index);
            if (source == null || source.getTask() == null || source.getTask().isBlank()) {
                continue;
            }

            String assigneeName = source.getName() != null && !source.getName().isBlank()
                    ? source.getName().trim() : "미지정";
            ProjectTodo todo = existingByIndex.get(index);
            if (todo == null) {
                todo = ProjectTodo.builder()
                        .project(minutes.getProject())
                        .minutes(minutes)
                        .sourceIndex(index)
                        .status(TodoStatus.TODO)
                        .priorityOrder(++nextPriority)
                        .build();
            }

            todo.setContent(source.getTask().trim());
            todo.setAssigneeName(assigneeName);
            todo.setAssigneeId(stableAssigneeId(minutes.getProject().getId(), assigneeName));
            todo.setDueDate(parseDueDate(source.getDeadline()));
            changed.add(todo);
        }

        if (!changed.isEmpty()) {
            todoRepository.saveAll(changed);
        }
    }

    @Transactional
    public ProjectTodoResponse updateStatus(String projectId, String todoId, TodoStatus status) {
        ProjectTodo todo = findOwnedTodo(projectId, todoId);
        todo.setStatus(status);
        todo.setCompletedAt(status == TodoStatus.COMPLETED ? LocalDateTime.now() : null);
        return toResponse(todo);
    }

    @Transactional
    public List<ProjectTodoResponse> reorder(String projectId, List<String> orderedTodoIds) {
        if (orderedTodoIds == null || orderedTodoIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Todo order is required");
        }

        List<ProjectTodo> activeTodos = todoRepository
                .findByProjectIdAndStatusOrderByPriorityOrderAscCreatedAtAsc(projectId, TodoStatus.TODO);
        Map<String, ProjectTodo> byId = activeTodos.stream()
                .collect(Collectors.toMap(ProjectTodo::getId, Function.identity()));

        if (orderedTodoIds.size() != activeTodos.size()
                || !byId.keySet().equals(new HashSet<>(orderedTodoIds))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Todo order does not match active todos");
        }

        for (int index = 0; index < orderedTodoIds.size(); index++) {
            byId.get(orderedTodoIds.get(index)).setPriorityOrder(index + 1);
        }

        return orderedTodoIds.stream()
                .map(byId::get)
                .map(this::toResponse)
                .toList();
    }

    private ProjectTodo findOwnedTodo(String projectId, String todoId) {
        return todoRepository.findByIdAndProjectId(todoId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Todo not found"));
    }

    private String stableAssigneeId(String projectId, String assigneeName) {
        return UUID.nameUUIDFromBytes((projectId + ":" + assigneeName)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private LocalDate parseDueDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private ProjectTodoResponse toResponse(ProjectTodo todo) {
        return ProjectTodoResponse.builder()
                .id(todo.getId())
                .content(todo.getContent())
                .assignee(TodoAssigneeResponse.builder()
                        .id(todo.getAssigneeId())
                        .name(todo.getAssigneeName())
                        .build())
                .meetingNoteId(todo.getMinutes() != null ? todo.getMinutes().getId() : null)
                .status(todo.getStatus())
                .priorityOrder(todo.getPriorityOrder())
                .dueDate(todo.getDueDate())
                .completedAt(format(todo.getCompletedAt()))
                .createdAt(format(todo.getCreatedAt()))
                .updatedAt(format(todo.getUpdatedAt()))
                .build();
    }

    private String format(LocalDateTime value) {
        return value != null ? value.toString() : null;
    }
}
