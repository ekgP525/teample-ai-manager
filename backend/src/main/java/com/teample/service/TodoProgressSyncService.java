package com.teample.service;

import com.teample.entity.Minutes;
import com.teample.entity.Project;
import com.teample.entity.ProjectTodo;
import com.teample.entity.TodoData;
import com.teample.entity.TodoMemberProgress;
import com.teample.repository.MinutesRepository;
import com.teample.repository.ProjectTodoRepository;
import com.teample.repository.TodoMemberProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TodoProgressSyncService {

    private static final String UNASSIGNED = "UNASSIGNED";
    private static final String ALL_KO = "\uC804\uCCB4";
    private static final String EVERYONE_KO = "\uBAA8\uB450";
    private static final String TEAM_ALL_KO = "\uD300\uC6D0 \uC804\uCCB4";
    private static final Pattern ASSIGNEE_SEPARATOR = Pattern.compile("[,/;|&]+|\\band\\b", Pattern.CASE_INSENSITIVE);

    private final MinutesRepository minutesRepository;
    private final ProjectTodoRepository projectTodoRepository;
    private final TodoMemberProgressRepository todoMemberProgressRepository;

    @Transactional
    public void syncProject(Project project) {
        List<Minutes> minutesList = minutesRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
        for (Minutes minutes : minutesList) {
            syncMinutes(project, minutes);
        }
    }

    @Transactional
    public void syncMinutes(Project project, Minutes minutes) {
        List<TodoData> sourceTodos = minutes.getTodos() != null ? minutes.getTodos() : List.of();
        Set<Integer> activeSourceIndexes = new HashSet<>();

        for (int index = 0; index < sourceTodos.size(); index++) {
            final int sourceIndex = index;
            TodoData sourceTodo = sourceTodos.get(index);
            if (sourceTodo == null || sourceTodo.getTask() == null || sourceTodo.getTask().isBlank()) {
                continue;
            }

            activeSourceIndexes.add(sourceIndex);
            ProjectTodo projectTodo = projectTodoRepository
                    .findByProjectIdAndMinutesIdAndSourceIndex(project.getId(), minutes.getId(), sourceIndex)
                    .orElseGet(() -> ProjectTodo.builder()
                            .project(project)
                            .minutes(minutes)
                            .sourceIndex(sourceIndex)
                            .build());

            projectTodo.setSourceAssignee(normalizeOptional(sourceTodo.getName()));
            projectTodo.setTask(sourceTodo.getTask().trim());
            projectTodo.setDeadline(normalizeOptional(sourceTodo.getDeadline()));
            ProjectTodo savedTodo = projectTodoRepository.save(projectTodo);

            syncProgressRows(project, minutes, savedTodo, sourceTodo);
        }

        removeDeletedSourceTodos(minutes.getId(), activeSourceIndexes);
    }

    @Transactional
    public void deleteByMinutes(Minutes minutes) {
        todoMemberProgressRepository.deleteByMinutesId(minutes.getId());
        projectTodoRepository.deleteByMinutesId(minutes.getId());
    }

    @Transactional
    public void deleteByProject(Project project) {
        todoMemberProgressRepository.deleteByProjectId(project.getId());
        projectTodoRepository.deleteByProjectId(project.getId());
    }

    private void syncProgressRows(Project project, Minutes minutes, ProjectTodo projectTodo, TodoData sourceTodo) {
        List<MemberRef> assignedMembers = resolveAssignedMembers(sourceTodo.getName(), project.getMembers());
        Set<String> activeUserIds = new HashSet<>();
        Map<String, TodoMemberProgress> existingByUserId = new LinkedHashMap<>();

        for (TodoMemberProgress progress : todoMemberProgressRepository.findByTodoId(projectTodo.getId())) {
            existingByUserId.put(progress.getUserId(), progress);
        }

        for (MemberRef member : assignedMembers) {
            activeUserIds.add(member.userId());
            TodoMemberProgress progress = existingByUserId.get(member.userId());
            if (progress == null) {
                progress = TodoMemberProgress.builder()
                        .todo(projectTodo)
                        .project(project)
                        .minutes(minutes)
                        .userId(member.userId())
                        .memberName(member.memberName())
                        .assigned(true)
                        .completed(false)
                        .status(TodoMemberProgress.STATUS_TODO)
                        .build();
            } else {
                progress.setProject(project);
                progress.setMinutes(minutes);
                progress.setMemberName(member.memberName());
                progress.setAssigned(true);
            }
            todoMemberProgressRepository.save(progress);
        }

        for (TodoMemberProgress progress : existingByUserId.values()) {
            if (!activeUserIds.contains(progress.getUserId()) && Boolean.TRUE.equals(progress.getAssigned())) {
                progress.setAssigned(false);
                todoMemberProgressRepository.save(progress);
            }
        }
    }

    private void removeDeletedSourceTodos(String minutesId, Set<Integer> activeSourceIndexes) {
        for (ProjectTodo projectTodo : projectTodoRepository.findByMinutesId(minutesId)) {
            if (!activeSourceIndexes.contains(projectTodo.getSourceIndex())) {
                todoMemberProgressRepository.deleteByTodoId(projectTodo.getId());
                projectTodoRepository.delete(projectTodo);
            }
        }
    }

    private List<MemberRef> resolveAssignedMembers(String sourceAssignee, List<String> projectMembers) {
        List<MemberRef> members = normalizeProjectMembers(projectMembers);
        String assignee = normalizeOptional(sourceAssignee);

        if (assignee == null) {
            return List.of(new MemberRef(UNASSIGNED, UNASSIGNED));
        }

        if (isAllAssignee(assignee)) {
            return members.isEmpty() ? List.of(new MemberRef(UNASSIGNED, UNASSIGNED)) : members;
        }

        List<MemberRef> resolved = new ArrayList<>();
        for (String token : ASSIGNEE_SEPARATOR.split(assignee)) {
            String memberName = normalizeOptional(token);
            if (memberName == null) {
                continue;
            }
            if (isAllAssignee(memberName)) {
                return members.isEmpty() ? List.of(new MemberRef(UNASSIGNED, UNASSIGNED)) : members;
            }
            resolved.add(resolveMember(memberName, members));
        }

        if (resolved.isEmpty()) {
            resolved.add(resolveMember(assignee, members));
        }
        return dedupe(resolved);
    }

    private List<MemberRef> normalizeProjectMembers(List<String> projectMembers) {
        if (projectMembers == null) {
            return List.of();
        }

        return projectMembers.stream()
                .map(this::normalizeOptional)
                .filter(memberName -> memberName != null)
                .map(memberName -> new MemberRef(memberName, memberName))
                .distinct()
                .toList();
    }

    private MemberRef resolveMember(String memberName, List<MemberRef> projectMembers) {
        return projectMembers.stream()
                .filter(member -> member.memberName().equalsIgnoreCase(memberName))
                .findFirst()
                .orElseGet(() -> new MemberRef(memberName, memberName));
    }

    private List<MemberRef> dedupe(List<MemberRef> members) {
        Map<String, MemberRef> deduped = new LinkedHashMap<>();
        for (MemberRef member : members) {
            deduped.putIfAbsent(member.userId(), member);
        }
        return new ArrayList<>(deduped.values());
    }

    private boolean isAllAssignee(String assignee) {
        String normalized = assignee.trim().toLowerCase();
        return normalized.equals("all")
                || normalized.equals("team")
                || normalized.equals("everyone")
                || normalized.equals(ALL_KO)
                || normalized.equals(EVERYONE_KO)
                || normalized.equals(TEAM_ALL_KO);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record MemberRef(String userId, String memberName) {
    }
}