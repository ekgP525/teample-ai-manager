package com.teample.service;

import com.teample.dto.MinutesRequest;
import com.teample.dto.MinutesResponse;
import com.teample.entity.Minutes;
import com.teample.entity.Project;
import com.teample.entity.ProjectStatus;
import com.teample.repository.MinutesRepository;
import com.teample.repository.ProjectRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MinutesServiceTest {

    @Test
    void legacyMinutesWithoutEvidenceOrJsonListsRemainReadable() {
        MinutesRepository minutesRepository = mock(MinutesRepository.class);
        Minutes minutes = Minutes.builder().id("minutes-id").build();
        when(minutesRepository.findById("minutes-id")).thenReturn(Optional.of(minutes));
        MinutesService service = new MinutesService(
                minutesRepository, mock(ProjectRepository.class), mock(ClaudeService.class),
                mock(ProjectTodoService.class), mock(TodoProgressSyncService.class));

        MinutesResponse response = service.findById("minutes-id").orElseThrow();

        assertThat(response.getEvidence()).isNull();
        assertThat(response.getDiscussions()).isEmpty();
        assertThat(response.getDecisions()).isEmpty();
        assertThat(response.getPending()).isEmpty();
        assertThat(response.getTodos()).isEmpty();
        assertThat(response.getNextAgenda()).isEmpty();
    }

    @Test
    void endedProjectCannotCreateNewMinutes() {
        MinutesRepository minutesRepository = mock(MinutesRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ClaudeService claudeService = mock(ClaudeService.class);
        Project project = Project.builder().status(ProjectStatus.ENDED).build();
        when(projectRepository.findById("project-id")).thenReturn(Optional.of(project));
        MinutesService service = new MinutesService(
                minutesRepository, projectRepository, claudeService,
                mock(ProjectTodoService.class), mock(TodoProgressSyncService.class));

        MinutesRequest request = new MinutesRequest();
        request.setMeetingDate(LocalDate.now().toString());
        request.setRawText("conversation");

        assertThatThrownBy(() -> service.create("project-id", request))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(claudeService);
        verify(minutesRepository, never()).save(any());
    }
}
