package com.teample.service;

import com.teample.dto.dashboard.MyProjectDashboardResponse;
import com.teample.dto.dashboard.TeamProjectDashboardResponse;
import com.teample.dto.dashboard.TodoAssignmentResponse;
import com.teample.dto.dashboard.TodoProgressUpdateRequest;
import com.teample.entity.Minutes;
import com.teample.entity.Project;
import com.teample.entity.ProjectTodo;
import com.teample.entity.TodoMemberProgress;
import com.teample.exception.TodoAccessDeniedException;
import com.teample.repository.IntegratedTodoRepository;
import com.teample.repository.ProjectMemberRepository;
import com.teample.repository.ProjectRepository;
import com.teample.repository.ProjectTodoRepository;
import com.teample.repository.TodoMemberProgressRepository;
import com.teample.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private IntegratedTodoRepository integratedTodoRepository;

    @Mock
    private ProjectTodoRepository projectTodoRepository;

    @Mock
    private TodoMemberProgressRepository todoMemberProgressRepository;

    @Mock
    private TodoProgressSyncService todoProgressSyncService;

    @Mock
    private ProjectMemberService projectMemberService;

    private DashboardService dashboardService;
    private Project project;
    private Minutes minutes;
    private ProjectTodo sharedTodo;
    private TodoMemberProgress aliceProgress;
    private TodoMemberProgress bobProgress;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                projectRepository,
                projectMemberRepository,
                integratedTodoRepository,
                projectTodoRepository,
                todoMemberProgressRepository,
                todoProgressSyncService,
                projectMemberService
        );

        project = Project.builder()
                .id("project-1")
                .name("Dashboard Test")
                .members(List.of("alice", "bob"))
                .build();
        minutes = Minutes.builder()
                .id("minutes-1")
                .project(project)
                .meetingDate(LocalDate.now())
                .rawText("meeting raw text")
                .title("Weekly sync")
                .build();
        sharedTodo = ProjectTodo.builder()
                .id("todo-1")
                .project(project)
                .minutes(minutes)
                .sourceIndex(0)
                .sourceAssignee("all")
                .task("Share schedule by DM")
                .deadline("2099-01-01")
                .build();
        aliceProgress = progress("assignment-alice", "alice", false, sharedTodo);
        bobProgress = progress("assignment-bob", "bob", false, sharedTodo);
    }

    @Test
    void completingMyTodoDoesNotChangeOtherMembersProgress() {
        when(todoMemberProgressRepository.findById("assignment-alice"))
                .thenReturn(Optional.of(aliceProgress));
        when(todoMemberProgressRepository.save(any(TodoMemberProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(todoMemberProgressRepository.findByProjectIdAndUserIdAndAssignedTrue("project-1", "alice"))
                .thenReturn(List.of(aliceProgress));
        when(todoMemberProgressRepository.findByProjectIdAndUserIdAndAssignedTrue("project-1", "bob"))
                .thenReturn(List.of(bobProgress));

        TodoProgressUpdateRequest request = new TodoProgressUpdateRequest();
        request.setCompleted(true);
        dashboardService.updateProgressByAssignmentId("assignment-alice", "alice", request);

        MyProjectDashboardResponse aliceDashboard = dashboardService.findMyProjectDashboard("project-1", "alice")
                .orElseThrow();
        MyProjectDashboardResponse bobDashboard = dashboardService.findMyProjectDashboard("project-1", "bob")
                .orElseThrow();

        assertThat(aliceDashboard.getTodos().get(0).getCompleted()).isTrue();
        assertThat(bobDashboard.getTodos().get(0).getCompleted()).isFalse();
    }

    @Test
    void cannotUpdateAnotherMembersAssignment() {
        when(todoMemberProgressRepository.findById("assignment-bob"))
                .thenReturn(Optional.of(bobProgress));

        TodoProgressUpdateRequest request = new TodoProgressUpdateRequest();
        request.setCompleted(true);

        assertThatThrownBy(() -> dashboardService.updateProgressByAssignmentId("assignment-bob", "alice", request))
                .isInstanceOf(TodoAccessDeniedException.class);

        assertThat(bobProgress.getCompleted()).isFalse();
        verify(todoMemberProgressRepository, never()).save(any(TodoMemberProgress.class));
    }

    @Test
    void sameTodoCanHaveIndependentMemberProgressRows() {
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(todoMemberProgressRepository.findByProjectIdAndAssignedTrue("project-1"))
                .thenReturn(List.of(aliceProgress, bobProgress));
        when(projectTodoRepository.findByProjectId("project-1"))
                .thenReturn(List.of(sharedTodo));

        TeamProjectDashboardResponse dashboard = dashboardService.findTeamProjectDashboard("project-1", "alice")
                .orElseThrow();

        assertThat(dashboard.getTodos()).hasSize(1);
        assertThat(dashboard.getTodos().get(0).getAssignments())
                .extracting(TodoAssignmentResponse::getUserId)
                .containsExactly("alice", "bob");
        assertThat(dashboard.getTodos().get(0).getAssignments())
                .allMatch(assignment -> assignment.getTodoId().equals("todo-1"));
    }

    @Test
    void calculatesPersonalProgressRateOnlyFromCurrentUsersTodos() {
        ProjectTodo secondAliceTodo = ProjectTodo.builder()
                .id("todo-2")
                .project(project)
                .minutes(minutes)
                .sourceIndex(1)
                .sourceAssignee("alice")
                .task("Review schema")
                .deadline("2099-01-02")
                .build();
        TodoMemberProgress doneAliceProgress = progress("assignment-alice-done", "alice", true, sharedTodo);
        TodoMemberProgress pendingAliceProgress = progress("assignment-alice-pending", "alice", false, secondAliceTodo);

        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(todoMemberProgressRepository.findByProjectIdAndUserIdAndAssignedTrue("project-1", "alice"))
                .thenReturn(List.of(doneAliceProgress, pendingAliceProgress));
        when(todoMemberProgressRepository.findByProjectIdAndUserIdAndAssignedTrue("project-1", "bob"))
                .thenReturn(List.of(bobProgress));

        MyProjectDashboardResponse aliceDashboard = dashboardService.findMyProjectDashboard("project-1", "alice")
                .orElseThrow();
        MyProjectDashboardResponse bobDashboard = dashboardService.findMyProjectDashboard("project-1", "bob")
                .orElseThrow();

        assertThat(aliceDashboard.getTotalTodoCount()).isEqualTo(2);
        assertThat(aliceDashboard.getCompletedTodoCount()).isEqualTo(1);
        assertThat(aliceDashboard.getProgressRate()).isEqualTo(50);
        assertThat(bobDashboard.getTotalTodoCount()).isEqualTo(1);
        assertThat(bobDashboard.getCompletedTodoCount()).isZero();
        assertThat(bobDashboard.getProgressRate()).isZero();
    }

    @Test
    void teamProgressSummaryIgnoresUnassignedTodosConsistently() {
        ProjectTodo unassignedTodo = ProjectTodo.builder()
                .id("todo-unassigned")
                .project(project)
                .minutes(minutes)
                .sourceIndex(2)
                .sourceAssignee("unknown")
                .task("Unassigned follow-up")
                .deadline("2099-01-03")
                .build();
        TodoMemberProgress doneAliceProgress = progress("assignment-alice-done", "alice", true, sharedTodo);
        TodoMemberProgress pendingBobProgress = progress("assignment-bob-pending", "bob", false, sharedTodo);

        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(todoMemberProgressRepository.findByProjectIdAndAssignedTrue("project-1"))
                .thenReturn(List.of(doneAliceProgress, pendingBobProgress));
        when(projectTodoRepository.findByProjectId("project-1"))
                .thenReturn(List.of(sharedTodo, unassignedTodo));

        TeamProjectDashboardResponse dashboard = dashboardService.findTeamProjectDashboard("project-1", "alice")
                .orElseThrow();

        assertThat(dashboard.getTodos()).hasSize(2);
        assertThat(dashboard.getTodos())
                .filteredOn(todo -> todo.getTodoId().equals("todo-unassigned"))
                .singleElement()
                .satisfies(todo -> assertThat(todo.getAssignments()).isEmpty());
        assertThat(dashboard.getTotalTodoCount()).isEqualTo(2);
        assertThat(dashboard.getCompletedTodoCount()).isEqualTo(1);
        assertThat(dashboard.getPendingTodoCount()).isEqualTo(1);
        assertThat(dashboard.getProgressRate()).isEqualTo(50);
    }

    @Test
    void teamProgressSummaryHandlesEmptyTotalWithExistingProgressPolicy() {
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(todoMemberProgressRepository.findByProjectIdAndAssignedTrue("project-1"))
                .thenReturn(List.of());
        when(projectTodoRepository.findByProjectId("project-1"))
                .thenReturn(List.of());

        TeamProjectDashboardResponse dashboard = dashboardService.findTeamProjectDashboard("project-1", "alice")
                .orElseThrow();

        assertThat(dashboard.getTotalTodoCount()).isZero();
        assertThat(dashboard.getCompletedTodoCount()).isZero();
        assertThat(dashboard.getPendingTodoCount()).isZero();
        assertThat(dashboard.getProgressRate()).isEqualTo(100);
        assertThat(dashboard.getMembers()).hasSize(2);
        assertThat(dashboard.getTodos()).isEmpty();
    }

    @Test
    void nonProjectMemberCannotReadProjectDashboard() {
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> dashboardService.findMyProjectDashboard("project-1", "mallory"))
                .isInstanceOf(TodoAccessDeniedException.class);
        assertThatThrownBy(() -> dashboardService.findTeamProjectDashboard("project-1", "mallory"))
                .isInstanceOf(TodoAccessDeniedException.class);
    }
    @Test
    void jwtDashboardUsesProjectMemberAccessAndLegacyProgressKey() {
        AuthenticatedUser user = new AuthenticatedUser("supabase-user-id", "alice", "alice@example.com");
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(todoMemberProgressRepository.findByProjectIdAndUserIdAndAssignedTrue("project-1", "alice"))
                .thenReturn(List.of(aliceProgress));

        MyProjectDashboardResponse dashboard = dashboardService.findMyProjectDashboard("project-1", user, false)
                .orElseThrow();

        verify(projectMemberService).ensureProjectMember(project, user, false);
        assertThat(dashboard.getUserId()).isEqualTo("alice");
        assertThat(dashboard.getTodos()).hasSize(1);
    }
    @Test
    void jwtDashboardSkipsSyncWhenProgressRowsAlreadyExist() {
        AuthenticatedUser user = new AuthenticatedUser("supabase-user-id", "alice", "alice@example.com");
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(todoMemberProgressRepository.existsByProjectId("project-1")).thenReturn(true);
        when(todoMemberProgressRepository.findByProjectIdAndUserIdAndAssignedTrue("project-1", "alice"))
                .thenReturn(List.of(aliceProgress));

        MyProjectDashboardResponse dashboard = dashboardService.findMyProjectDashboard("project-1", user, false)
                .orElseThrow();

        verify(todoProgressSyncService, never()).syncProject(project);
        assertThat(dashboard.getTodos()).hasSize(1);
    }

    private TodoMemberProgress progress(String id, String userId, boolean completed, ProjectTodo todo) {
        TodoMemberProgress progress = TodoMemberProgress.builder()
                .id(id)
                .todo(todo)
                .project(project)
                .minutes(minutes)
                .userId(userId)
                .memberName(userId)
                .assigned(true)
                .completed(false)
                .status(TodoMemberProgress.STATUS_TODO)
                .build();
        if (completed) {
            progress.setCompletedState(true);
        }
        return progress;
    }
}
