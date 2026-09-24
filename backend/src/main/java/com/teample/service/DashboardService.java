package com.teample.service;

import com.teample.dto.dashboard.DashboardMemberResponse;
import com.teample.dto.dashboard.DashboardProjectResponse;
import com.teample.dto.dashboard.DashboardTodoResponse;
import com.teample.dto.dashboard.MyProjectDashboardResponse;
import com.teample.dto.dashboard.ProjectDashboardResponse;
import com.teample.dto.dashboard.TeamMemberProgressResponse;
import com.teample.dto.dashboard.TeamProjectDashboardResponse;
import com.teample.dto.dashboard.TeamTodoProgressResponse;
import com.teample.dto.dashboard.TodoAssignmentResponse;
import com.teample.dto.dashboard.TodoProgressUpdateRequest;
import com.teample.entity.IntegratedTodo;
import com.teample.entity.Project;
import com.teample.entity.ProjectMember;
import com.teample.entity.ProjectStatus;
import com.teample.entity.ProjectTodo;
import com.teample.entity.TodoMemberProgress;
import com.teample.entity.TodoStatus;
import com.teample.repository.IntegratedTodoRepository;
import com.teample.repository.ProjectMemberRepository;
import com.teample.repository.ProjectRepository;
import com.teample.repository.ProjectTodoRepository;
import com.teample.repository.TodoMemberProgressRepository;
import com.teample.exception.TodoAccessDeniedException;
import com.teample.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final ProjectMemberRepository projectMemberRepository;
    private final IntegratedTodoRepository integratedTodoRepository;
    private final ProjectTodoRepository projectTodoRepository;
    private final TodoMemberProgressRepository todoMemberProgressRepository;
    private final TodoProgressSyncService todoProgressSyncService;
    private final ProjectMemberService projectMemberService;


    @Transactional
    public List<MyProjectDashboardResponse> findMyProjectDashboards(AuthenticatedUser user, boolean admin) {
        String resolvedUserId = dashboardUserId(user);
        if (resolvedUserId == null) {
            return List.of();
        }

        return findAccessibleVisibleProjects(user, admin).stream()
                .map(project -> {
                    syncProjectIfDashboardRowsMissing(project);
                    return buildMyProjectDashboard(project, resolvedUserId);
                })
                .filter(dashboard -> dashboard.getTotalTodoCount() > 0)
                .toList();
    }

    @Transactional
    public Optional<MyProjectDashboardResponse> findMyProjectDashboard(String projectId, AuthenticatedUser user, boolean admin) {
        String resolvedUserId = dashboardUserId(user);
        if (resolvedUserId == null) {
            return Optional.empty();
        }

        return projectRepository.findById(projectId).map(project -> {
            projectMemberService.ensureProjectMember(project, user, admin);
            syncProjectIfDashboardRowsMissing(project);
            return buildMyProjectDashboard(project, resolvedUserId);
        });
    }

    @Transactional
    public Optional<TeamProjectDashboardResponse> findTeamProjectDashboard(String projectId, AuthenticatedUser user, boolean admin) {
        String resolvedUserId = dashboardUserId(user);
        if (resolvedUserId == null) {
            return Optional.empty();
        }

        return projectRepository.findById(projectId).map(project -> {
            projectMemberService.ensureProjectMember(project, user, admin);
            syncProjectIfDashboardRowsMissing(project);
            return buildTeamProjectDashboard(project);
        });
    }

    @Transactional
    public List<DashboardProjectResponse> findLegacyProjectDashboards(AuthenticatedUser user, boolean admin) {
        return findMyProjectDashboards(user, admin).stream()
                .map(this::toLegacyProjectResponse)
                .toList();
    }

    @Transactional
    public Optional<ProjectDashboardResponse> findLegacyProjectDashboard(String projectId, AuthenticatedUser user, boolean admin) {
        String resolvedUserId = dashboardUserId(user);
        if (resolvedUserId == null) {
            return Optional.empty();
        }

        return projectRepository.findById(projectId).map(project -> {
            projectMemberService.ensureProjectMember(project, user, admin);
            syncProjectIfDashboardRowsMissing(project);
            TeamProjectDashboardResponse teamDashboard = buildTeamProjectDashboard(project);
            List<DashboardMemberResponse> members = teamDashboard.getMembers().stream()
                    .map(this::toLegacyMemberResponse)
                    .toList();

            DashboardMemberResponse selectedMember = resolveSelectedMember(members, resolvedUserId);

            return ProjectDashboardResponse.builder()
                    .projectId(project.getId())
                    .projectName(project.getName())
                    .selectedUserId(selectedMember.getUserId())
                    .selectedMemberName(selectedMember.getMemberName())
                    .selectedMember(selectedMember)
                    .teamMembers(members)
                    .build();
        });
    }

    @Transactional
    public Optional<TodoAssignmentResponse> updateProgressByAssignmentId(
            String assignmentId,
            AuthenticatedUser user,
            boolean admin,
            TodoProgressUpdateRequest request
    ) {
        String resolvedUserId = dashboardUserId(user);
        if (resolvedUserId == null) {
            return Optional.empty();
        }

        return todoMemberProgressRepository.findById(assignmentId)
                .map(progress -> {
                    ensureProgressProjectAccess(progress, user, admin);
                    ensureOwnProgress(progress, resolvedUserId);
                    ensureAssigned(progress);
                    return updateProgress(progress, request);
                });
    }

    @Transactional
    public Optional<TodoAssignmentResponse> updateProgressByTodoAndUser(
            String todoId,
            AuthenticatedUser user,
            boolean admin,
            TodoProgressUpdateRequest request
    ) {
        String resolvedUserId = dashboardUserId(user);
        if (resolvedUserId == null) {
            return Optional.empty();
        }

        return findProgressByClientTodoId(todoId, resolvedUserId)
                .filter(progress -> Boolean.TRUE.equals(progress.getAssigned()))
                .map(progress -> {
                    ensureProgressProjectAccess(progress, user, admin);
                    return updateProgress(progress, request);
                });
    }
    @Transactional
    public List<MyProjectDashboardResponse> findMyProjectDashboards(String userId) {
        String resolvedUserId = normalizeOptionalUserId(userId);
        if (resolvedUserId == null) {
            return List.of();
        }

        return projectRepository.findAll().stream()
                .filter(Project::isVisibleInActiveList)
                .filter(project -> isProjectMember(project, resolvedUserId))
                .map(project -> {
                    syncProjectIfDashboardRowsMissing(project);
                    return buildMyProjectDashboard(project, resolvedUserId);
                })
                .filter(dashboard -> dashboard.getTotalTodoCount() > 0)
                .toList();
    }

    @Transactional
    public Optional<MyProjectDashboardResponse> findMyProjectDashboard(String projectId, String userId) {
        String resolvedUserId = normalizeOptionalUserId(userId);
        if (resolvedUserId == null) {
            return Optional.empty();
        }

        return projectRepository.findById(projectId).map(project -> {
            ensureProjectMember(project, resolvedUserId);
            syncProjectIfDashboardRowsMissing(project);
            return buildMyProjectDashboard(project, resolvedUserId);
        });
    }

    @Transactional
    public Optional<TeamProjectDashboardResponse> findTeamProjectDashboard(String projectId, String currentUserId) {
        String resolvedUserId = normalizeOptionalUserId(currentUserId);
        if (resolvedUserId == null) {
            return Optional.empty();
        }

        return projectRepository.findById(projectId).map(project -> {
            ensureProjectMember(project, resolvedUserId);
            syncProjectIfDashboardRowsMissing(project);
            return buildTeamProjectDashboard(project);
        });
    }

    @Transactional
    public Optional<TodoAssignmentResponse> updateProgressByAssignmentId(String assignmentId, String currentUserId, TodoProgressUpdateRequest request) {
        String resolvedUserId = normalizeOptionalUserId(currentUserId);
        if (resolvedUserId == null) {
            return Optional.empty();
        }

        return todoMemberProgressRepository.findById(assignmentId)
                .map(progress -> {
                    ensureOwnProgress(progress, resolvedUserId);
                    ensureAssigned(progress);
                    return updateProgress(progress, request);
                });
    }

    @Transactional
    public Optional<TodoAssignmentResponse> updateProgressByTodoAndUser(String todoId, String currentUserId, TodoProgressUpdateRequest request) {
        String resolvedUserId = normalizeOptionalUserId(currentUserId);
        if (resolvedUserId == null) {
            return Optional.empty();
        }

        return findProgressByClientTodoId(todoId, resolvedUserId)
                .filter(progress -> Boolean.TRUE.equals(progress.getAssigned()))
                .map(progress -> updateProgress(progress, request));
    }

    @Transactional
    public List<DashboardProjectResponse> findLegacyProjectDashboards(String userId) {
        return findMyProjectDashboards(userId).stream()
                .map(this::toLegacyProjectResponse)
                .toList();
    }

    @Transactional
    public Optional<ProjectDashboardResponse> findLegacyProjectDashboard(String projectId, String userId) {
        String resolvedUserId = normalizeOptionalUserId(userId);
        if (resolvedUserId == null) {
            return Optional.empty();
        }

        return projectRepository.findById(projectId).map(project -> {
            ensureProjectMember(project, resolvedUserId);
            syncProjectIfDashboardRowsMissing(project);
            TeamProjectDashboardResponse teamDashboard = buildTeamProjectDashboard(project);
            List<DashboardMemberResponse> members = teamDashboard.getMembers().stream()
                    .map(this::toLegacyMemberResponse)
                    .toList();

            DashboardMemberResponse selectedMember = resolveSelectedMember(members, resolvedUserId);

            return ProjectDashboardResponse.builder()
                    .projectId(project.getId())
                    .projectName(project.getName())
                    .selectedUserId(selectedMember.getUserId())
                    .selectedMemberName(selectedMember.getMemberName())
                    .selectedMember(selectedMember)
                    .teamMembers(members)
                    .build();
        });
    }

    private List<Project> findAccessibleVisibleProjects(AuthenticatedUser user, boolean admin) {
        if (admin) {
            return projectRepository.findByStatusNot(ProjectStatus.DELETED).stream()
                    .filter(Project::isVisibleInActiveList)
                    .toList();
        }
        if (user == null) {
            return List.of();
        }

        Map<String, Project> projectsById = new LinkedHashMap<>();
        projectMemberRepository.findByUserIdOrderByJoinedAtAsc(user.authUserId()).stream()
                .map(ProjectMember::getProject)
                .filter(project -> project != null && project.getId() != null)
                .filter(Project::isVisibleInActiveList)
                .forEach(project -> projectsById.putIfAbsent(project.getId(), project));

        projectRepository.findAll().stream()
                .filter(Project::isVisibleInActiveList)
                .filter(project -> project.getId() != null && !projectsById.containsKey(project.getId()))
                .filter(project -> !projectMemberRepository.existsByProjectId(project.getId()))
                .filter(project -> projectMemberService.canAccessProject(project, user, false))
                .forEach(project -> projectsById.putIfAbsent(project.getId(), project));

        return new ArrayList<>(projectsById.values());
    }

    private void syncProjectIfDashboardRowsMissing(Project project) {
        if (project.getId() == null) return;

        List<TodoMemberProgress> assignedRows = todoMemberProgressRepository
                .findByProjectIdAndAssignedTrue(project.getId());
        if (assignedRows.isEmpty()) {
            todoProgressSyncService.syncProject(project);
            return;
        }

        List<String> memberIds = projectMemberRepository.findByProjectIdOrderByJoinedAtAsc(project.getId()).stream()
                .map(ProjectMember::getUserId)
                .toList();
        boolean hasLegacyOrRemovedAssignments = assignedRows.stream()
                .map(TodoMemberProgress::getUserId)
                .anyMatch(userId -> !UNASSIGNED.equals(userId) && !memberIds.contains(userId));
        if (hasLegacyOrRemovedAssignments) {
            todoProgressSyncService.syncProject(project);
        }
    }

    private MyProjectDashboardResponse buildMyProjectDashboard(Project project, String userId) {
        List<TodoMemberProgress> progressRows = todoMemberProgressRepository
                .findByProjectIdAndUserIdAndAssignedTrue(project.getId(), userId)
                .stream()
                .sorted(progressComparator())
                .toList();
        List<TodoAssignmentResponse> todos = progressRows.stream()
                .map(this::toAssignmentResponse)
                .toList();
        int completedCount = countCompleted(todos);

        return MyProjectDashboardResponse.builder()
                .projectId(project.getId())
                .projectName(project.getName())
                .userId(userId)
                .memberName(resolveMemberName(todos, userId))
                .target(resolveTarget(todos))
                .totalTodoCount(todos.size())
                .completedTodoCount(completedCount)
                .pendingTodoCount(todos.size() - completedCount)
                .progressRate(calculateProgressRate(todos.size(), completedCount))
                .todos(todos)
                .build();
    }

    private TeamProjectDashboardResponse buildTeamProjectDashboard(Project project) {
        List<TodoMemberProgress> progressRows = todoMemberProgressRepository.findByProjectIdAndAssignedTrue(project.getId())
                .stream()
                .sorted(progressComparator())
                .toList();
        Map<String, List<TodoMemberProgress>> progressByUser = initMemberProgressMap(project);

        for (TodoMemberProgress progress : progressRows) {
            progressByUser.computeIfAbsent(progress.getUserId(), ignored -> new ArrayList<>()).add(progress);
        }

        List<TeamMemberProgressResponse> members = progressByUser.entrySet().stream()
                .map(entry -> {
                    TeamMemberProgressResponse response = buildTeamMemberResponse(entry.getKey(), entry.getValue());
                    projectMemberRepository.findByProjectIdOrderByJoinedAtAsc(project.getId()).stream()
                        .filter(member -> member.getUserId().equals(entry.getKey())).findFirst()
                        .ifPresent(member -> response.setMemberName(member.getDisplayName()));
                    return response;
                })
                .toList();

        Map<String, List<TodoMemberProgress>> progressByTodo = new LinkedHashMap<>();
        for (TodoMemberProgress progress : progressRows) {
            progressByTodo.computeIfAbsent(progress.getTodo().getId(), ignored -> new ArrayList<>()).add(progress);
        }

        List<TeamTodoProgressResponse> todos = projectTodoRepository.findByProjectId(project.getId()).stream()
                .sorted(projectTodoComparator())
                .map(todo -> buildTeamTodoResponse(todo, progressByTodo.getOrDefault(todo.getId(), List.of())))
                .toList();
        int totalTodoCount = progressRows.size();
        int completedTodoCount = countCompletedProgressRows(progressRows);

        return TeamProjectDashboardResponse.builder()
                .projectId(project.getId())
                .projectName(project.getName())
                .totalTodoCount(totalTodoCount)
                .completedTodoCount(completedTodoCount)
                .pendingTodoCount(totalTodoCount - completedTodoCount)
                .progressRate(calculateProgressRate(totalTodoCount, completedTodoCount))
                .members(members)
                .todos(todos)
                .build();
    }

    private void ensureProjectMember(Project project, String currentUserId) {
        if (!isProjectMember(project, currentUserId)) {
            throw new TodoAccessDeniedException("Only project members can access this dashboard.");
        }
    }

    private boolean isProjectMember(Project project, String currentUserId) {
        String resolvedUserId = normalizeOptionalUserId(currentUserId);
        return resolvedUserId != null && projectMemberRepository.existsByProjectIdAndUserId(project.getId(), resolvedUserId);
    }

    private void ensureProgressProjectAccess(TodoMemberProgress progress, AuthenticatedUser user, boolean admin) {
        Project project = progress.getProject() != null ? progress.getProject() : progress.getTodo().getProject();
        projectMemberService.ensureProjectMember(project, user, admin);
    }

    private void ensureAssigned(TodoMemberProgress progress) {
        if (!Boolean.TRUE.equals(progress.getAssigned())) {
            throw new TodoAccessDeniedException("Only assigned todo progress can be updated.");
        }
    }

    private void ensureOwnProgress(TodoMemberProgress progress, String currentUserId) {
        if (!currentUserId.equals(progress.getUserId())) {
            throw new TodoAccessDeniedException("Only the assigned user can update this todo progress.");
        }
    }

    private TodoAssignmentResponse updateProgress(TodoMemberProgress progress, TodoProgressUpdateRequest request) {
        Boolean completed = resolveCompleted(request);
        if (completed != null) {
            progress.setCompletedState(completed);
        }
        TodoMemberProgress saved = todoMemberProgressRepository.save(progress);
        syncIntegratedTodoStatus(saved.getTodo());
        return toAssignmentResponse(saved);
    }

    private Optional<TodoMemberProgress> findProgressByClientTodoId(String todoId, String userId) {
        Optional<TodoMemberProgress> legacyProgress = todoMemberProgressRepository.findByTodoIdAndUserId(todoId, userId);
        if (legacyProgress.isPresent()) {
            return legacyProgress;
        }

        return integratedTodoRepository.findById(todoId)
                .flatMap(this::findDashboardTodo)
                .flatMap(todo -> todoMemberProgressRepository.findByTodoIdAndUserId(todo.getId(), userId));
    }

    private Optional<ProjectTodo> findDashboardTodo(IntegratedTodo todo) {
        if (todo.getProject() == null || todo.getMinutes() == null || todo.getSourceIndex() == null) {
            return Optional.empty();
        }
        return projectTodoRepository.findByProjectIdAndMinutesIdAndSourceIndex(
                todo.getProject().getId(), todo.getMinutes().getId(), todo.getSourceIndex());
    }

    private void syncIntegratedTodoStatus(ProjectTodo dashboardTodo) {
        findIntegratedTodo(dashboardTodo).ifPresent(integratedTodo -> {
            List<TodoMemberProgress> progressRows = todoMemberProgressRepository
                    .findByTodoIdAndAssignedTrue(dashboardTodo.getId());
            boolean completed = !progressRows.isEmpty()
                    && progressRows.stream().allMatch(progressRow -> Boolean.TRUE.equals(progressRow.getCompleted()));
            integratedTodo.setStatus(completed ? TodoStatus.COMPLETED : TodoStatus.TODO);
            integratedTodo.setCompletedAt(completed ? LocalDateTime.now() : null);
            integratedTodoRepository.save(integratedTodo);
        });
    }

    private Boolean resolveCompleted(TodoProgressUpdateRequest request) {
        if (request == null) {
            return null;
        }
        if (request.getCompleted() != null) {
            return request.getCompleted();
        }
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            return null;
        }
        String status = request.getStatus().trim().toUpperCase();
        if (TodoMemberProgress.STATUS_DONE.equals(status)) {
            return true;
        }
        if (TodoMemberProgress.STATUS_TODO.equals(status)) {
            return false;
        }
        throw new IllegalArgumentException("status must be TODO or DONE");
    }

    private TeamMemberProgressResponse buildTeamMemberResponse(String userId, List<TodoMemberProgress> progressRows) {
        List<TodoAssignmentResponse> todos = progressRows.stream()
                .sorted(progressComparator())
                .map(this::toAssignmentResponse)
                .toList();
        int completedCount = countCompleted(todos);

        return TeamMemberProgressResponse.builder()
                .userId(userId)
                .memberName(resolveMemberName(todos, userId))
                .totalTodoCount(todos.size())
                .completedTodoCount(completedCount)
                .pendingTodoCount(todos.size() - completedCount)
                .progressRate(calculateProgressRate(todos.size(), completedCount))
                .todos(todos)
                .build();
    }

    private TeamTodoProgressResponse buildTeamTodoResponse(ProjectTodo todo, List<TodoMemberProgress> progressRows) {
        List<TodoAssignmentResponse> assignments = progressRows.stream()
                .sorted(progressComparator())
                .map(this::toAssignmentResponse)
                .toList();

        return TeamTodoProgressResponse.builder()
                .todoId(resolveClientTodoId(todo))
                .projectId(todo.getProject().getId())
                .projectName(todo.getProject().getName())
                .minutesId(todo.getMinutes().getId())
                .minutesTitle(todo.getMinutes().getTitle())
                .task(todo.getTask())
                .deadline(todo.getDeadline())
                .sourceAssignee(todo.getSourceAssignee())
                .assignments(assignments)
                .build();
    }

    private TodoAssignmentResponse toAssignmentResponse(TodoMemberProgress progress) {
        ProjectTodo todo = progress.getTodo();
        Project project = progress.getProject() != null ? progress.getProject() : todo.getProject();

        return TodoAssignmentResponse.builder()
                .assignmentId(progress.getId())
                .todoId(resolveClientTodoId(todo))
                .projectId(project.getId())
                .projectName(project.getName())
                .minutesId(todo.getMinutes().getId())
                .minutesTitle(todo.getMinutes().getTitle())
                .userId(progress.getUserId())
                .memberName(progress.getMemberName())
                .task(todo.getTask())
                .deadline(todo.getDeadline())
                .assigned(progress.getAssigned())
                .completed(progress.getCompleted())
                .status(progress.getStatus())
                .completedAt(progress.getCompletedAt())
                .deadlineStatus(resolveDeadlineStatus(todo.getDeadline()))
                .build();
    }

    private DashboardProjectResponse toLegacyProjectResponse(MyProjectDashboardResponse response) {
        List<DashboardTodoResponse> todos = response.getTodos().stream()
                .map(this::toDashboardTodoResponse)
                .toList();

        return DashboardProjectResponse.builder()
                .projectId(response.getProjectId())
                .projectName(response.getProjectName())
                .userId(response.getUserId())
                .memberName(response.getMemberName())
                .target(response.getTarget())
                .totalTodoCount(response.getTotalTodoCount())
                .completedTodoCount(response.getCompletedTodoCount())
                .pendingTodoCount(response.getPendingTodoCount())
                .onTrackTodoCount(countByDeadlineStatus(todos, ON_TRACK))
                .overdueTodoCount(countByDeadlineStatus(todos, OVERDUE))
                .progressRate(response.getProgressRate())
                .todos(todos)
                .build();
    }

    private DashboardMemberResponse toLegacyMemberResponse(TeamMemberProgressResponse response) {
        List<DashboardTodoResponse> todos = response.getTodos().stream()
                .map(this::toDashboardTodoResponse)
                .toList();

        return DashboardMemberResponse.builder()
                .userId(response.getUserId())
                .memberName(response.getMemberName())
                .totalTodoCount(response.getTotalTodoCount())
                .completedTodoCount(response.getCompletedTodoCount())
                .pendingTodoCount(response.getPendingTodoCount())
                .onTrackTodoCount(countByDeadlineStatus(todos, ON_TRACK))
                .overdueTodoCount(countByDeadlineStatus(todos, OVERDUE))
                .noDeadlineTodoCount(countByDeadlineStatus(todos, NO_DEADLINE))
                .unknownDeadlineTodoCount(countByDeadlineStatus(todos, UNKNOWN_DEADLINE))
                .progressRate(response.getProgressRate())
                .todos(todos)
                .build();
    }

    private DashboardTodoResponse toDashboardTodoResponse(TodoAssignmentResponse response) {
        return DashboardTodoResponse.builder()
                .assignmentId(response.getAssignmentId())
                .todoId(response.getTodoId())
                .projectId(response.getProjectId())
                .projectName(response.getProjectName())
                .minutesId(response.getMinutesId())
                .minutesTitle(response.getMinutesTitle())
                .userId(response.getUserId())
                .memberName(response.getMemberName())
                .task(response.getTask())
                .deadline(response.getDeadline())
                .assigned(response.getAssigned())
                .completed(response.getCompleted())
                .status(response.getStatus())
                .completedAt(response.getCompletedAt())
                .deadlineStatus(response.getDeadlineStatus())
                .build();
    }

    private DashboardMemberResponse resolveSelectedMember(List<DashboardMemberResponse> members, String userId) {
        if (userId != null) {
            return members.stream()
                    .filter(member -> userId.equals(member.getUserId()))
                    .findFirst()
                    .orElseGet(() -> emptyLegacyMember(userId));
        }

        return members.stream()
                .filter(member -> member.getTotalTodoCount() > 0)
                .findFirst()
                .orElseGet(() -> members.stream().findFirst().orElseGet(() -> emptyLegacyMember(UNASSIGNED)));
    }

    private DashboardMemberResponse emptyLegacyMember(String userId) {
        return DashboardMemberResponse.builder()
                .userId(userId)
                .memberName(userId)
                .totalTodoCount(0)
                .completedTodoCount(0)
                .pendingTodoCount(0)
                .progressRate(100)
                .todos(List.of())
                .build();
    }

    private Map<String, List<TodoMemberProgress>> initMemberProgressMap(Project project) {
        List<String> accountMemberNames = projectMemberRepository.findByProjectIdOrderByJoinedAtAsc(project.getId()).stream()
                .map(ProjectMember::getUserId)
                .toList();
        if (!accountMemberNames.isEmpty()) {
            return initMemberProgressMap(accountMemberNames);
        }
        return new LinkedHashMap<>();
    }

    private Map<String, List<TodoMemberProgress>> initMemberProgressMap(List<String> members) {
        Map<String, List<TodoMemberProgress>> progressByUser = new LinkedHashMap<>();
        if (members == null) {
            return progressByUser;
        }

        for (String member : members) {
            String normalizedMember = normalizeOptionalUserId(member);
            if (normalizedMember != null) {
                progressByUser.putIfAbsent(normalizedMember, new ArrayList<>());
            }
        }
        return progressByUser;
    }

    private String resolveTarget(List<TodoAssignmentResponse> todos) {
        return todos.stream()
                .filter(todo -> !Boolean.TRUE.equals(todo.getCompleted()))
                .map(TodoAssignmentResponse::getTask)
                .filter(task -> task != null && !task.isBlank())
                .findFirst()
                .orElseGet(() -> todos.stream()
                        .map(TodoAssignmentResponse::getTask)
                        .filter(task -> task != null && !task.isBlank())
                        .findFirst()
                        .orElse("No task"));
    }

    private String resolveMemberName(List<TodoAssignmentResponse> todos, String userId) {
        return todos.stream()
                .map(TodoAssignmentResponse::getMemberName)
                .filter(memberName -> memberName != null && !memberName.isBlank())
                .findFirst()
                .orElse(userId);
    }

    private int countCompleted(List<TodoAssignmentResponse> todos) {
        return (int) todos.stream()
                .filter(todo -> Boolean.TRUE.equals(todo.getCompleted()))
                .count();
    }

    private int countCompletedProgressRows(List<TodoMemberProgress> progressRows) {
        return (int) progressRows.stream()
                .filter(progress -> Boolean.TRUE.equals(progress.getCompleted()))
                .count();
    }

    private int countByDeadlineStatus(List<DashboardTodoResponse> todos, String status) {
        return (int) todos.stream()
                .filter(todo -> status.equals(todo.getDeadlineStatus()))
                .count();
    }

    private int calculateProgressRate(int totalCount, int completedCount) {
        if (totalCount == 0) {
            return 100;
        }
        return (int) Math.round(((double) completedCount / totalCount) * 100);
    }

    private String resolveClientTodoId(ProjectTodo todo) {
        return findIntegratedTodo(todo)
                .map(IntegratedTodo::getId)
                .orElse(todo.getId());
    }

    private Optional<IntegratedTodo> findIntegratedTodo(ProjectTodo todo) {
        if (todo == null || todo.getProject() == null || todo.getMinutes() == null || todo.getSourceIndex() == null) {
            return Optional.empty();
        }
        return integratedTodoRepository.findByProjectIdAndMinutesIdAndSourceIndex(
                todo.getProject().getId(), todo.getMinutes().getId(), todo.getSourceIndex());
    }

    private String resolveDeadlineStatus(String deadline) {
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

    private Comparator<TodoMemberProgress> progressComparator() {
        return Comparator
                .comparingInt((TodoMemberProgress progress) -> Boolean.TRUE.equals(progress.getCompleted()) ? 1 : 0)
                .thenComparing(progress -> parseDeadlineOrMax(progress.getTodo().getDeadline()))
                .thenComparing(progress -> progress.getTodo().getTask(), Comparator.nullsLast(String::compareTo))
                .thenComparing(TodoMemberProgress::getMemberName, Comparator.nullsLast(String::compareTo));
    }

    private Comparator<ProjectTodo> projectTodoComparator() {
        return Comparator
                .comparing((ProjectTodo todo) -> todo.getMinutes().getCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ProjectTodo::getSourceIndex, Comparator.nullsLast(Integer::compareTo));
    }

    private LocalDate parseDeadlineOrMax(String deadline) {
        try {
            return LocalDate.parse(deadline);
        } catch (DateTimeParseException | NullPointerException e) {
            return LocalDate.MAX;
        }
    }

    private String dashboardUserId(AuthenticatedUser user) {
        return user == null ? null : normalizeOptionalUserId(user.authUserId());
    }

    private String normalizeOptionalUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return userId.trim();
    }
}
