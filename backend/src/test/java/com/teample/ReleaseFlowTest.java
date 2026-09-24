package com.teample;

import com.teample.dto.*;
import com.teample.dto.dashboard.TodoProgressUpdateRequest;
import com.teample.entity.*;
import com.teample.repository.*;
import com.teample.security.*;
import com.teample.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ReleaseFlowTest {
    @Autowired ProjectService projects;
    @Autowired ProjectInvitationService invitations;
    @Autowired MinutesGenerationService generation;
    @Autowired MinutesService minutes;
    @Autowired ProjectTodoService board;
    @Autowired DashboardService dashboard;
    @Autowired ProjectMemberService access;
    @Autowired AiUsageService usage;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean ClaudeService claude;
    private AuthenticatedUser alice;
    private AuthenticatedUser bob;

    @BeforeEach void setup() {
        alice = new AuthenticatedUser(UUID.randomUUID().toString(), "Alice", null);
        bob = new AuthenticatedUser(UUID.randomUUID().toString(), "Bob", null);
        jdbc.update("delete from ai_requests");
        when(claude.analyze(anyString(), anyString(), anyList())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat((List<String>) invocation.getArgument(2)).contains("Alice");
            return result(List.of(new TodoData("Alice", "Task A", "2026-12-01"), new TodoData("Bob", "Task B", "2026-12-02")));
        });
    }

    private String project() {
        ProjectRequest request = new ProjectRequest(); request.setName("Release test");
        return projects.create(request, alice).getId();
    }
    private MinutesRequest request() {
        MinutesRequest request = new MinutesRequest(); request.setMeetingDate(LocalDate.now().toString()); request.setRawText("Alice: Task A. Bob: Task B.");
        return request;
    }
    private ClaudeService.MinutesResult result(List<TodoData> todos) {
        return new ClaudeService.MinutesResult("Meeting", "Topic", List.of(), List.of(), List.of(), todos, List.of(), null);
    }

    @Test void twoAccountsGenerateCompleteDeleteItemAndRetryWithoutDuplicateAiCall() {
        String project = project();
        var invitation = invitations.create(project, alice, false);
        assertThat(invitations.findActive(project, alice, false).orElseThrow().code()).isEqualTo(invitation.code());
        assertThatThrownBy(() -> invitations.findActive(project, bob, false)).isInstanceOf(ProjectMemberService.ProjectMemberAccessDeniedException.class);
        invitations.join(invitation.code(), bob);
        String key = UUID.randomUUID().toString();
        var generated = generation.create(project, request(), alice, false, key);
        assertThat(generation.create(project, request(), alice, false, key).getId()).isEqualTo(generated.getId());
        verify(claude, times(1)).analyze(anyString(), anyString(), eq(List.of("Alice", "Bob")));
        var aliceTodo = dashboard.findMyProjectDashboard(project, alice, false).orElseThrow().getTodos().getFirst();
        assertThat(aliceTodo.getUserId()).isEqualTo(alice.authUserId());
        TodoProgressUpdateRequest completed = new TodoProgressUpdateRequest(); completed.setCompleted(true);
        assertThatThrownBy(() -> dashboard.updateProgressByAssignmentId(aliceTodo.getAssignmentId(), bob, false, completed))
                .isInstanceOf(com.teample.exception.TodoAccessDeniedException.class);
        dashboard.updateProgressByAssignmentId(aliceTodo.getAssignmentId(), alice, false, completed);
        generated.setTodos(List.of(generated.getTodos().get(1)));
        minutes.update(project, generated.getId(), generated).orElseThrow();
        assertThat(board.findByProject(project, TodoStatus.TODO)).extracting(ProjectTodoResponse::getContent).containsExactly("Task B");
        assertThat(board.findByProject(project, TodoStatus.COMPLETED)).isEmpty();
        assertThat(dashboard.findMyProjectDashboard(project, bob, false).orElseThrow().getTodos()).hasSize(1);
        assertThat(dashboard.findMyProjectDashboard(project, alice, false).orElseThrow().getTodos()).isEmpty();
    }

    @Test void sameDisplayNamesDoNotSharePersonalAssignmentsAndAllUsesAccountIds() {
        String project = project();
        AuthenticatedUser twin = new AuthenticatedUser(UUID.randomUUID().toString(), "Alice", null);
        invitations.join(invitations.create(project, alice, false).code(), twin);
        doReturn(result(List.of(new TodoData("all", "Shared", "")))).when(claude).analyze(anyString(), anyString(), anyList());
        generation.create(project, request(), alice, false, UUID.randomUUID().toString());
        var first = dashboard.findMyProjectDashboard(project, alice, false).orElseThrow().getTodos();
        var second = dashboard.findMyProjectDashboard(project, twin, false).orElseThrow().getTodos();
        assertThat(first).hasSize(1); assertThat(second).hasSize(1);
        assertThat(first.getFirst().getAssignmentId()).isNotEqualTo(second.getFirst().getAssignmentId());
        TodoProgressUpdateRequest completed = new TodoProgressUpdateRequest(); completed.setCompleted(true);
        dashboard.updateProgressByAssignmentId(first.getFirst().getAssignmentId(), alice, false, completed);
        assertThat(dashboard.findMyProjectDashboard(project, twin, false).orElseThrow().getTodos().getFirst().getCompleted()).isFalse();
        doReturn(result(List.of(new TodoData("Alice", "Ambiguous owner", "")))).when(claude).analyze(anyString(), anyString(), anyList());
        generation.create(project, request(), alice, false, UUID.randomUUID().toString());
        assertThat(dashboard.findMyProjectDashboard(project, alice, false).orElseThrow().getTodos()).hasSize(1);
        assertThat(dashboard.findMyProjectDashboard(project, twin, false).orElseThrow().getTodos()).hasSize(1);
        assertThat(dashboard.findTeamProjectDashboard(project, alice, false).orElseThrow().getMembers())
                .anySatisfy(member -> { assertThat(member.getUserId()).isEqualTo("UNASSIGNED"); assertThat(member.getTotalTodoCount()).isEqualTo(1); });
    }

    @Test void legacyCompletionMigratesOnlyToUniqueRosterAccount() {
        String project = project();
        doReturn(result(List.of(new TodoData("Alice", "Legacy task", "")))).when(claude).analyze(anyString(), anyString(), anyList());
        generation.create(project, request(), alice, false, UUID.randomUUID().toString());
        jdbc.update("update todo_member_progress set user_id = 'Alice', completed = true, status = 'DONE' where project_id = ?", project);
        var tasks = dashboard.findMyProjectDashboard(project, alice, false).orElseThrow().getTodos();
        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().getUserId()).isEqualTo(alice.authUserId());
        assertThat(tasks.getFirst().getCompleted()).isTrue();
    }

    @Test void deletedProjectRejectsExistingInvitesAndOwnerOnlyOperations() {
        String project = project();
        String code = invitations.create(project, alice, false).code();
        assertThatThrownBy(() -> access.ensureProjectOwner(project, bob, false)).isInstanceOf(ProjectMemberService.ProjectMemberAccessDeniedException.class);
        projects.delete(project);
        assertThatThrownBy(() -> invitations.join(code, bob)).isInstanceOf(ProjectInvitationService.InvitationExpiredException.class);
        assertThatThrownBy(() -> invitations.create(project, alice, false)).isInstanceOf(ProjectInvitationService.InvitationExpiredException.class);
    }

    @Test void invalidDateFailsBeforePaidCallOrUsageReservation() {
        MinutesRequest request = request(); request.setMeetingDate("2026-02-30");
        assertThatThrownBy(() -> generation.create(project(), request, alice, false, UUID.randomUUID().toString()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(claude, never()).analyze(anyString(), anyString(), anyList());
        assertThat(jdbc.queryForObject("select count(*) from ai_requests", Integer.class)).isZero();
    }

    @Test void limitsArePersistentAndIdempotencyKeysCannotChangePayload() {
        String key = UUID.randomUUID().toString();
        usage.claim(alice.authUserId(), key, "fingerprint");
        assertThatThrownBy(() -> usage.claim(alice.authUserId(), UUID.randomUUID().toString(), "second"))
                .hasMessageContaining("429");
        assertThatThrownBy(() -> usage.claim(alice.authUserId(), key, "different")).hasMessageContaining("409");
        usage.fail(alice.authUserId(), key);
        for (int i = 1; i < 20; i++) { String next = UUID.randomUUID().toString(); usage.claim(alice.authUserId(), next, "fingerprint"); usage.fail(alice.authUserId(), next); }
        assertThatThrownBy(() -> usage.claim(alice.authUserId(), UUID.randomUUID().toString(), "fingerprint")).hasMessageContaining("429");
    }

    @Test void concurrentAdmissionCannotExceedGlobalLimit() throws Exception {
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int index = 0; index < 8; index++) {
                jobs.add(executor.submit(() -> {
                    try { usage.claim(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "fingerprint"); return true; }
                    catch (org.springframework.web.server.ResponseStatusException e) { assertThat(e.getStatusCode().value()).isEqualTo(429); return false; }
                }));
            }
            int accepted = 0;
            for (var job : jobs) if (job.get(15, java.util.concurrent.TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isEqualTo(4);
        }
    }

    @Test void failedAiCallLeavesNoMinutesAndFreesConcurrencySlot() {
        String project = project();
        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "AI unavailable"))
                .when(claude).analyze(anyString(), anyString(), anyList());
        String key = UUID.randomUUID().toString();
        assertThatThrownBy(() -> generation.create(project, request(), alice, false, key)).hasMessageContaining("502");
        assertThat(minutes.findByProjectId(project)).isEmpty();
        assertThat(jdbc.queryForObject("select status from ai_requests where user_id = ? and request_key = ?", String.class, alice.authUserId(), key)).isEqualTo("FAILED");
        assertThatCode(() -> usage.claim(alice.authUserId(), UUID.randomUUID().toString(), "new request")).doesNotThrowAnyException();
    }

    @Test void corsPreflightAndUnauthorizedErrorsAreReadableAndDefaultAdminIsDisabled() throws Exception {
        mvc.perform(options("/api/projects").header("Origin", "https://release.example.com")
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "https://release.example.com"));
        mvc.perform(get("/api/projects").header("Origin", "https://release.example.com"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("Access-Control-Allow-Origin", "https://release.example.com"));
        mvc.perform(get("/api/projects").header("X-Admin-Id", "admin").header("X-Admin-Password", "1234"))
                .andExpect(status().isUnauthorized());
        mvc.perform(options("/api/projects").header("Origin", "https://attacker.vercel.app").header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
