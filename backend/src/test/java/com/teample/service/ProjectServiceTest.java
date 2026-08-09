package com.teample.service;

import com.teample.dto.ProjectRequest;
import com.teample.dto.ProjectResponse;
import com.teample.entity.Project;
import com.teample.entity.ProjectStatus;
import com.teample.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Test
    void createKeepsLegacyRequestActiveWhenDeadlineIsNull() {
        ProjectRequest request = new ProjectRequest();
        request.setName("legacy");
        request.setMembers(List.of("member"));
        when(projectRepository.save(any(Project.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectResponse response = new ProjectService(projectRepository).create(request);

        assertThat(response.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(response.getDisposalDeadline()).isNull();
    }

    @Test
    void createMarksProjectWithFutureDeadlineAsScheduled() {
        ProjectRequest request = new ProjectRequest();
        request.setName("scheduled");
        request.setMembers(List.of("member"));
        request.setDisposalDeadline(LocalDate.now().plusDays(7));
        when(projectRepository.save(any(Project.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectResponse response = new ProjectService(projectRepository).create(request);

        assertThat(response.getStatus()).isEqualTo(ProjectStatus.DISPOSAL_SCHEDULED);
    }

    @Test
    void expiredExistingProjectTransitionsToDisposed() {
        Project project = Project.builder()
                .name("expired")
                .members(List.of())
                .status(ProjectStatus.DISPOSAL_SCHEDULED)
                .disposalDeadline(LocalDate.now().minusDays(1))
                .build();
        when(projectRepository.findAll()).thenReturn(List.of(project));

        ProjectResponse response = new ProjectService(projectRepository).findAll().get(0);

        assertThat(response.getStatus()).isEqualTo(ProjectStatus.DISPOSED);
        assertThat(response.getDisposedAt()).isNotNull();
    }

    @Test
    void deleteSoftDeletesWithoutRemovingRow() {
        Project project = Project.builder().status(ProjectStatus.ACTIVE).build();
        when(projectRepository.findById("project-id")).thenReturn(Optional.of(project));

        boolean deleted = new ProjectService(projectRepository).delete("project-id");

        assertThat(deleted).isTrue();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.DISPOSED);
        assertThat(project.getDisposedAt()).isNotNull();
        verify(projectRepository, never()).delete(any(Project.class));
    }
}
