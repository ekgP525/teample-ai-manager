package com.teample.service;

import com.teample.dto.ProjectTodoResponse;
import com.teample.dto.TodoAssigneeResponse;
import com.teample.entity.*;
import com.teample.repository.IntegratedTodoRepository;
import com.teample.repository.MinutesRepository;
import com.teample.repository.ProjectRepository;
import com.teample.repository.ProjectTodoRepository;
import com.teample.repository.TodoMemberProgressRepository;
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

    private final IntegratedTodoRepository todoRepository;
    private final ProjectRepository projectRepository;
    private final MinutesRepository minutesRepository;
    private final ProjectTodoRepository projectTodoRepository;
    private final TodoMemberProgressRepository todoMemberProgressRepository;

    @Transactional
    public List<ProjectTodoResponse> findByProject(String projectId, TodoStatus status) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        TodoStatus requestedStatus = status != null ? status : TodoStatus.TODO;
        // 동기화는 회의록 생성·수정·삭제 시점에만 한다. 조회마다 쓰면 동시 요청이 유니크 제약에서 충돌한다.
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
        if (TodoIdentity.assignMissingIds(sourceTodos)) {
            minutesRepository.save(minutes);
        }

        List<IntegratedTodo> existing = todoRepository.findByMinutesIdOrderBySourceIndexAsc(minutes.getId());
        Map<String, IntegratedTodo> existingById = new HashMap<>();
        Map<Integer, IntegratedTodo> legacyByIndex = new HashMap<>();
        for (IntegratedTodo todo : existing) {
            if (todo.getSourceTodoId() != null) {
                existingById.putIfAbsent(todo.getSourceTodoId(), todo);
            } else if (todo.getSourceIndex() != null) {
                legacyByIndex.putIfAbsent(todo.getSourceIndex(), todo);
            }
        }

        int nextPriority = todoRepository.findMaxPriorityOrderByProjectId(minutes.getProject().getId());
        List<IntegratedTodo> changed = new ArrayList<>();
        Set<String> activeSourceIds = new HashSet<>();

        for (int index = 0; index < sourceTodos.size(); index++) {
            TodoData source = sourceTodos.get(index);
            if (source == null || source.getTask() == null || source.getTask().isBlank()) {
                continue;
            }
            activeSourceIds.add(source.getId());

            String assigneeName = source.getName() != null && !source.getName().isBlank()
                    ? source.getName().trim() : "UNASSIGNED";
            IntegratedTodo todo = existingById.get(source.getId());
            if (todo == null) {
                // ID가 없던 시절의 행은 처음 한 번 위치로 이어 붙이고 ID를 채운다.
                todo = legacyByIndex.remove(index);
            }
            if (todo == null) {
                todo = IntegratedTodo.builder()
                        .project(minutes.getProject())
                        .minutes(minutes)
                        .status(TodoStatus.TODO)
                        .priorityOrder(++nextPriority)
                        .build();
            }

            todo.setSourceTodoId(source.getId());
            todo.setSourceIndex(index);
            todo.setContent(source.getTask().trim());
            todo.setAssigneeName(assigneeName);
            todo.setAssigneeId(stableAssigneeId(minutes.getProject().getId(), assigneeName));
            todo.setDueDate(parseDueDate(source.getDeadline()));
            changed.add(todo);
        }

        List<IntegratedTodo> removed = existing.stream()
                .filter(todo -> todo.getSourceTodoId() == null
                        ? !changed.contains(todo)
                        : !activeSourceIds.contains(todo.getSourceTodoId()))
                .toList();
        if (!removed.isEmpty()) {
            // 회의록에서 지워진 업무는 보드에서도 내린다. 유니크 인덱스 충돌을 피하려고 삭제를 먼저 반영한다.
            todoRepository.deleteAll(removed);
            todoRepository.flush();
        }
        if (!changed.isEmpty()) {
            todoRepository.saveAll(changed);
        }
    }

    /** 회의록 삭제 시 통합 업무 행도 함께 지운다. 그대로 두면 회의록 없는 고아 업무가 보드에 남는다. */
    @Transactional
    public void deleteByMinutes(Minutes minutes) {
        if (minutes == null || minutes.getId() == null) {
            return;
        }
        todoRepository.deleteByMinutesId(minutes.getId());
    }

    @Transactional
    public ProjectTodoResponse updateStatus(String projectId, String todoId, TodoStatus status) {
        IntegratedTodo todo = findOwnedTodo(projectId, todoId);
        todo.setStatus(status);
        todo.setCompletedAt(status == TodoStatus.COMPLETED ? LocalDateTime.now() : null);
        syncDashboardProgress(todo, status == TodoStatus.COMPLETED);
        return toResponse(todo);
    }

    @Transactional
    public List<ProjectTodoResponse> reorder(String projectId, List<String> orderedTodoIds) {
        if (orderedTodoIds == null || orderedTodoIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Todo order is required");
        }

        List<IntegratedTodo> activeTodos = todoRepository
                .findByProjectIdAndStatusOrderByPriorityOrderAscCreatedAtAsc(projectId, TodoStatus.TODO);
        Map<String, IntegratedTodo> byId = activeTodos.stream()
                .collect(Collectors.toMap(IntegratedTodo::getId, Function.identity()));

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

    private IntegratedTodo findOwnedTodo(String projectId, String todoId) {
        return todoRepository.findByIdAndProjectId(todoId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Todo not found"));
    }

    private void syncDashboardProgress(IntegratedTodo todo, boolean completed) {
        if (todo.getProject() == null || todo.getMinutes() == null) {
            return;
        }
        Optional<ProjectTodo> matched = todo.getSourceTodoId() != null
                ? projectTodoRepository.findByMinutesIdAndSourceTodoId(todo.getMinutes().getId(), todo.getSourceTodoId())
                : Optional.empty();
        if (matched.isEmpty() && todo.getSourceIndex() != null) {
            matched = projectTodoRepository.findByProjectIdAndMinutesIdAndSourceIndex(
                    todo.getProject().getId(), todo.getMinutes().getId(), todo.getSourceIndex());
        }
        matched.ifPresent(projectTodo -> todoMemberProgressRepository.findByTodoIdAndAssignedTrue(projectTodo.getId())
                        .forEach(progress -> {
                            progress.setCompletedState(completed);
                            todoMemberProgressRepository.save(progress);
                        }));
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

    private ProjectTodoResponse toResponse(IntegratedTodo todo) {
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
