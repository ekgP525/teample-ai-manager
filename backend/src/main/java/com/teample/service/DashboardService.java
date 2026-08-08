package com.teample.service;

import com.teample.dto.dashboard.DashboardMemberResponse;
import com.teample.dto.dashboard.DashboardProjectResponse;
import com.teample.dto.dashboard.DashboardTodoResponse;
import com.teample.dto.dashboard.ProjectDashboardResponse;
import com.teample.entity.Minutes;
import com.teample.entity.Project;
import com.teample.entity.TodoData;
import com.teample.repository.MinutesRepository;
import com.teample.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final String ON_TRACK = "ON_TRACK";
    private static final String OVERDUE = "OVERDUE";
    private static final String NO_DEADLINE = "NO_DEADLINE";
    private static final String UNKNOWN_DEADLINE = "UNKNOWN_DEADLINE";
    private static final String UNASSIGNED = "UNASSIGNED";

    private final ProjectRepository projectRepository;
    private final MinutesRepository minutesRepository;

    public List<DashboardProjectResponse> findMyProjectDashboards(String memberName) {
        String normalizedMemberName = normalizeOptionalMemberName(memberName);
        return projectRepository.findAll().stream()
                .map(project -> buildProjectSummary(project, normalizedMemberName))
                .filter(summary -> summary.getTotalTodoCount() > 0)
                .toList();
    }

    public Optional<ProjectDashboardResponse> findProjectDashboard(String projectId, String memberName) {
        return projectRepository.findById(projectId)
                .map(project -> buildProjectDashboard(project, normalizeOptionalMemberName(memberName)));
    }

    private DashboardProjectResponse buildProjectSummary(Project project, String memberName) {
        List<DashboardTodoResponse> todos = collectTodos(project).stream()
                .filter(todo -> memberName == null || memberName.equals(normalizeMemberName(todo.getMemberName())))
                .sorted(todoComparator())
                .toList();
        int overdueCount = countByStatus(todos, OVERDUE);
        int onTrackCount = todos.size() - overdueCount;

        return DashboardProjectResponse.builder()
                .projectId(project.getId())
                .projectName(project.getName())
                .target(resolveTarget(todos))
                .totalTodoCount(todos.size())
                .onTrackTodoCount(onTrackCount)
                .overdueTodoCount(overdueCount)
                .progressRate(calculateProgressRate(todos.size(), overdueCount))
                .todos(todos)
                .build();
    }

    private ProjectDashboardResponse buildProjectDashboard(Project project, String selectedMemberName) {
        List<DashboardTodoResponse> todos = collectTodos(project);
        Map<String, List<DashboardTodoResponse>> todosByMember = initMemberTodoMap(project.getMembers());

        for (DashboardTodoResponse todo : todos) {
            todosByMember.computeIfAbsent(
                    normalizeMemberName(todo.getMemberName()),
                    ignored -> new ArrayList<>()
            ).add(todo);
        }

        List<DashboardMemberResponse> teamMembers = todosByMember.entrySet().stream()
                .map(entry -> buildMemberResponse(entry.getKey(), entry.getValue()))
                .toList();
        String resolvedMemberName = selectedMemberName != null
                ? selectedMemberName
                : resolveDefaultMemberName(teamMembers);
        DashboardMemberResponse selectedMember = teamMembers.stream()
                .filter(member -> member.getMemberName().equals(resolvedMemberName))
                .findFirst()
                .orElseGet(() -> buildMemberResponse(resolvedMemberName, List.of()));

        return ProjectDashboardResponse.builder()
                .projectId(project.getId())
                .projectName(project.getName())
                .selectedMemberName(resolvedMemberName)
                .selectedMember(selectedMember)
                .teamMembers(teamMembers)
                .build();
    }

    private List<DashboardTodoResponse> collectTodos(Project project) {
        List<Minutes> minutesList = minutesRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
        List<DashboardTodoResponse> todos = new ArrayList<>();

        for (Minutes minutes : minutesList) {
            if (minutes.getTodos() == null) {
                continue;
            }

            for (TodoData todo : minutes.getTodos()) {
                todos.add(DashboardTodoResponse.builder()
                        .projectId(project.getId())
                        .projectName(project.getName())
                        .minutesId(minutes.getId())
                        .minutesTitle(minutes.getTitle())
                        .memberName(normalizeMemberName(todo.getName()))
                        .task(todo.getTask())
                        .deadline(todo.getDeadline())
                        .status(resolveStatus(todo.getDeadline()))
                        .build());
            }
        }

        return todos;
    }

    private Map<String, List<DashboardTodoResponse>> initMemberTodoMap(List<String> members) {
        Map<String, List<DashboardTodoResponse>> todosByMember = new LinkedHashMap<>();
        if (members == null) {
            return todosByMember;
        }

        for (String member : members) {
            todosByMember.put(normalizeMemberName(member), new ArrayList<>());
        }
        return todosByMember;
    }

    private DashboardMemberResponse buildMemberResponse(String memberName, List<DashboardTodoResponse> todos) {
        List<DashboardTodoResponse> sortedTodos = todos.stream()
                .sorted(todoComparator())
                .toList();
        int overdueCount = countByStatus(sortedTodos, OVERDUE);
        int noDeadlineCount = countByStatus(sortedTodos, NO_DEADLINE);
        int unknownDeadlineCount = countByStatus(sortedTodos, UNKNOWN_DEADLINE);
        int onTrackCount = sortedTodos.size() - overdueCount;

        return DashboardMemberResponse.builder()
                .memberName(memberName)
                .totalTodoCount(sortedTodos.size())
                .onTrackTodoCount(onTrackCount)
                .overdueTodoCount(overdueCount)
                .noDeadlineTodoCount(noDeadlineCount)
                .unknownDeadlineTodoCount(unknownDeadlineCount)
                .progressRate(calculateProgressRate(sortedTodos.size(), overdueCount))
                .todos(sortedTodos)
                .build();
    }

    private String resolveTarget(List<DashboardTodoResponse> todos) {
        return todos.stream()
                .map(DashboardTodoResponse::getTask)
                .filter(task -> task != null && !task.isBlank())
                .findFirst()
                .orElse("No task");
    }

    private String resolveDefaultMemberName(List<DashboardMemberResponse> teamMembers) {
        return teamMembers.stream()
                .filter(member -> member.getTotalTodoCount() > 0)
                .map(DashboardMemberResponse::getMemberName)
                .findFirst()
                .orElseGet(() -> teamMembers.stream()
                        .map(DashboardMemberResponse::getMemberName)
                        .findFirst()
                        .orElse(UNASSIGNED));
    }

    private int countByStatus(List<DashboardTodoResponse> todos, String status) {
        return (int) todos.stream()
                .filter(todo -> status.equals(todo.getStatus()))
                .count();
    }

    private int calculateProgressRate(int totalCount, int overdueCount) {
        if (totalCount == 0) {
            return 100;
        }
        return (int) Math.round(((double) (totalCount - overdueCount) / totalCount) * 100);
    }

    private String resolveStatus(String deadline) {
        if (deadline == null || deadline.isBlank()) {
            return NO_DEADLINE;
        }

        try {
            LocalDate deadlineDate = LocalDate.parse(deadline);
            return deadlineDate.isBefore(LocalDate.now()) ? OVERDUE : ON_TRACK;
        } catch (DateTimeParseException e) {
            return UNKNOWN_DEADLINE;
        }
    }

    private Comparator<DashboardTodoResponse> todoComparator() {
        return Comparator
                .comparingInt((DashboardTodoResponse todo) -> statusPriority(todo.getStatus()))
                .thenComparing(todo -> parseDeadlineOrMax(todo.getDeadline()))
                .thenComparing(DashboardTodoResponse::getTask, Comparator.nullsLast(String::compareTo));
    }

    private int statusPriority(String status) {
        return switch (status) {
            case OVERDUE -> 0;
            case ON_TRACK -> 1;
            case NO_DEADLINE -> 2;
            default -> 3;
        };
    }

    private LocalDate parseDeadlineOrMax(String deadline) {
        try {
            return LocalDate.parse(deadline);
        } catch (DateTimeParseException | NullPointerException e) {
            return LocalDate.MAX;
        }
    }

    private String normalizeOptionalMemberName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return normalizeMemberName(name);
    }

    private String normalizeMemberName(String name) {
        if (name == null || name.isBlank()) {
            return UNASSIGNED;
        }
        return name.trim();
    }
}

