package com.teample.service;

import com.teample.dto.ProjectRequest;
import com.teample.dto.ProjectResponse;
import com.teample.entity.Project;
import com.teample.entity.ProjectStatus;
import com.teample.repository.IntegratedTodoRepository;
import com.teample.repository.MinutesRepository;
import com.teample.repository.ProjectInvitationRepository;
import com.teample.repository.ProjectMemberRepository;
import com.teample.repository.ProjectRepository;
import com.teample.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private IntegratedTodoRepository integratedTodoRepository;

    @Mock
    private MinutesRepository minutesRepository;

    @Mock
    private TodoProgressSyncService todoProgressSyncService;

    @Mock
    private ProjectMemberService projectMemberService;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProjectInvitationRepository projectInvitationRepository;

    private ProjectService service;

    @BeforeEach
    void setUp() {
        service = new ProjectService(
                projectRepository,
                integratedTodoRepository,
                minutesRepository,
                todoProgressSyncService,
                projectMemberService,
                projectMemberRepository,
                projectInvitationRepository
        );
    }

    @Test
    void createKeepsRequestActiveWhenEndDateIsNull() {
        ProjectRequest request = new ProjectRequest();
        request.setName("legacy");
        request.setMembers(List.of("member"));
        when(projectRepository.save(any(Project.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectResponse response = service.create(request);

        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getEndDate()).isNull();
        assertThat(response.getDisposalDeadline()).isNull();
        verify(projectRepository).save(org.mockito.ArgumentMatchers.argThat(project -> project.getMembers().isEmpty()));
    }

    @Test
    void createWithOwnerRegistersProjectOwner() {
        ProjectRequest request = new ProjectRequest();
        request.setName("owned");
        request.setMembers(List.of("member"));
        AuthenticatedUser owner = new AuthenticatedUser("auth-user-1", "member", "member@example.com");
        when(projectRepository.save(any(Project.class)))
                .thenAnswer(invocation -> {
                    Project project = invocation.getArgument(0);
                    project.setId("project-id");
                    return project;
                });

        ProjectResponse response = service.create(request, owner);

        assertThat(response.getId()).isEqualTo("project-id");
        verify(projectMemberService).addOwner(any(Project.class), org.mockito.Mockito.eq(owner));
    }

    @Test
    void createMarksProjectWithEndDateWithinSevenDaysAsScheduled() {
        ProjectRequest request = new ProjectRequest();
        request.setName("scheduled");
        request.setMembers(List.of("member"));
        request.setEndDate(LocalDate.now().plusDays(7));
        when(projectRepository.save(any(Project.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectResponse response = service.create(request);

        assertThat(response.getStatus()).isEqualTo("DISPOSAL_SCHEDULED");
        assertThat(response.getEndDate()).isEqualTo(request.getEndDate());
        assertThat(response.getDisposalDeadline()).isEqualTo(request.getEndDate());
    }

    @Test
    void createKeepsProjectActiveWhenEndDateIsMoreThanSevenDaysAway() {
        ProjectRequest request = new ProjectRequest();
        request.setName("active future");
        request.setMembers(List.of("member"));
        request.setEndDate(LocalDate.now().plusDays(8));
        when(projectRepository.save(any(Project.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectResponse response = service.create(request);

        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getEndDate()).isEqualTo(request.getEndDate());
        assertThat(response.getDisposalDeadline()).isEqualTo(request.getEndDate());
    }

    @Test
    void expiredExistingProjectTransitionsToEnded() {
        Project project = Project.builder()
                .name("expired")
                .members(List.of())
                .status(ProjectStatus.END_SCHEDULED)
                .endDate(LocalDate.now().minusDays(1))
                .build();
        when(projectRepository.findAll()).thenReturn(List.of(project));

        ProjectResponse response = service.findAll().get(0);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ENDED);
        assertThat(response.getStatus()).isEqualTo("DISPOSED");
        assertThat(response.getEndedAt()).isNotNull();
        assertThat(response.getDisposedAt()).isNotNull();
    }

    @Test
    void findAllWithAuthenticatedUserReturnsAccessibleProjectsOnly() {
        AuthenticatedUser user = new AuthenticatedUser("auth-user-1", "member", "member@example.com");
        Project accessible = Project.builder().id("project-accessible").name("accessible").members(List.of("member")).build();
        Project blocked = Project.builder().id("project-blocked").name("blocked").members(List.of("other")).build();
        when(projectRepository.findAll()).thenReturn(List.of(accessible, blocked));
        when(projectMemberService.canAccessProject(accessible, user, false)).thenReturn(true);
        when(projectMemberService.canAccessProject(blocked, user, false)).thenReturn(false);

        List<ProjectResponse> responses = service.findAll(user, false);

        assertThat(responses).extracting(ProjectResponse::getId).containsExactly("project-accessible");
    }

    @Test
    void findTrashWithAuthenticatedUserReturnsAccessibleDeletedProjectsOnly() {
        AuthenticatedUser user = new AuthenticatedUser("auth-user-1", "member", "member@example.com");
        Project accessible = Project.builder()
                .id("deleted-accessible")
                .name("accessible")
                .status(ProjectStatus.DELETED)
                .build();
        Project blocked = Project.builder()
                .id("deleted-blocked")
                .name("blocked")
                .status(ProjectStatus.DELETED)
                .build();
        when(projectRepository.findByStatus(ProjectStatus.DELETED)).thenReturn(List.of(accessible, blocked));
        when(projectMemberService.canAccessProject(accessible, user, false)).thenReturn(true);
        when(projectMemberService.canAccessProject(blocked, user, false)).thenReturn(false);

        List<ProjectResponse> responses = service.findTrash(user, false);

        assertThat(responses).extracting(ProjectResponse::getId).containsExactly("deleted-accessible");
    }

    @Test
    void findAllWithAdminReturnsEveryVisibleProject() {
        AuthenticatedUser admin = new AuthenticatedUser("admin-test:admin", "admin", null);
        Project first = Project.builder().id("project-1").name("first").build();
        Project second = Project.builder().id("project-2").name("second").build();
        when(projectRepository.findAll()).thenReturn(List.of(first, second));
        when(projectMemberService.canAccessProject(first, admin, true)).thenReturn(true);
        when(projectMemberService.canAccessProject(second, admin, true)).thenReturn(true);

        List<ProjectResponse> responses = service.findAll(admin, true);

        assertThat(responses).extracting(ProjectResponse::getId).containsExactly("project-1", "project-2");
    }
    @Test
    void deleteMovesProjectToTrashWithoutRemovingRow() {
        Project project = Project.builder().status(ProjectStatus.ACTIVE).build();
        when(projectRepository.findById("project-id")).thenReturn(Optional.of(project));

        boolean deleted = service.delete("project-id");

        assertThat(deleted).isTrue();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.DELETED);
        assertThat(project.getDeletedAt()).isNotNull();
        verify(projectRepository, never()).delete(any(Project.class));
    }

    @Test
    void restoreOngoingProjectClearsDeletedAtAndBecomesActive() {
        LocalDate today = LocalDate.of(2026, 8, 17);
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 0);
        Project project = Project.builder()
                .status(ProjectStatus.DELETED)
                .deletedAt(now.minusDays(1))
                .build();

        project.restore(today, now);

        assertThat(project.getDeletedAt()).isNull();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(project.isDeleted()).isFalse();
    }

    @Test
    void restoreProjectWithEndDateWithinSevenDaysRecalculatesEndScheduledStatus() {
        LocalDate today = LocalDate.of(2026, 8, 17);
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 0);
        Project project = Project.builder()
                .status(ProjectStatus.DELETED)
                .endDate(today.plusDays(1))
                .deletedAt(now.minusDays(1))
                .build();

        project.restore(today, now);

        assertThat(project.getDeletedAt()).isNull();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.END_SCHEDULED);
        assertThat(project.isDeleted()).isFalse();
    }

    @Test
    void restoreProjectWithEndDateMoreThanSevenDaysAwayRecalculatesActiveStatus() {
        LocalDate today = LocalDate.of(2026, 8, 17);
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 0);
        Project project = Project.builder()
                .status(ProjectStatus.DELETED)
                .endDate(today.plusDays(8))
                .deletedAt(now.minusDays(1))
                .build();

        project.restore(today, now);

        assertThat(project.getDeletedAt()).isNull();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(project.isDeleted()).isFalse();
    }

    @Test
    void restoreExpiredProjectRecalculatesEndedStatus() {
        LocalDate today = LocalDate.of(2026, 8, 17);
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 0);
        Project project = Project.builder()
                .status(ProjectStatus.DELETED)
                .endDate(today.minusDays(1))
                .deletedAt(now.minusDays(1))
                .build();

        project.restore(today, now);

        assertThat(project.getDeletedAt()).isNull();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ENDED);
        assertThat(project.getEndedAt()).isEqualTo(now);
        assertThat(project.isDeleted()).isFalse();
    }

    @Test
    void restoreServiceMovesProjectOutOfTrashStatus() {
        LocalDateTime deletedAt = LocalDateTime.of(2026, 8, 16, 10, 0);
        Project project = Project.builder()
                .status(ProjectStatus.DELETED)
                .deletedAt(deletedAt)
                .build();
        when(projectRepository.findById("project-id")).thenReturn(Optional.of(project));

        Optional<ProjectResponse> response = service.restore("project-id");

        assertThat(response).isPresent();
        assertThat(response.get().getStatus()).isEqualTo("ACTIVE");
        assertThat(response.get().getDeletedAt()).isNull();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(project.isDeleted()).isFalse();
    }

    @Test
    void permanentlyDeleteRemovesProjectMembersBeforeProjectRow() {
        Project project = Project.builder().id("project-id").build();
        when(projectRepository.findById("project-id")).thenReturn(Optional.of(project));

        boolean deleted = service.permanentlyDelete("project-id");

        assertThat(deleted).isTrue();
        verify(projectMemberRepository).deleteByProjectId("project-id");
        verify(projectRepository).delete(project);
    }
}