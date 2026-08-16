package com.teample.service;

import com.teample.dto.ProjectRequest;
import com.teample.dto.ProjectResponse;
import com.teample.entity.Project;
import com.teample.entity.ProjectStatus;
import com.teample.repository.IntegratedTodoRepository;
import com.teample.repository.MinutesRepository;
import com.teample.repository.ProjectRepository;
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
import static org.mockito.Mockito.*;

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

    private ProjectService service;

    @BeforeEach
    void setUp() {
        service = new ProjectService(projectRepository, integratedTodoRepository, minutesRepository, todoProgressSyncService);
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
    }

    @Test
    void createMarksProjectWithFutureEndDateAsScheduled() {
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
    void restoreScheduledProjectRecalculatesEndScheduledStatus() {
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
}
