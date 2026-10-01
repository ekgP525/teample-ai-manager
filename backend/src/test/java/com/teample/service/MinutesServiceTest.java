package com.teample.service;

import com.teample.dto.MinutesRequest;
import com.teample.dto.MinutesResponse;
import com.teample.entity.Minutes;
import com.teample.entity.Project;
import com.teample.entity.ProjectStatus;
import com.teample.repository.MinutesRepository;
import com.teample.repository.ProjectMemberRepository;
import com.teample.repository.ProjectRepository;
import com.teample.repository.TranscriptionRepository;
import com.teample.dto.TodoItem;
import com.teample.entity.EvidenceData;
import com.teample.entity.TodoData;
import com.teample.entity.Transcription;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MinutesServiceTest {

    @Test
    void legacyMinutesWithoutEvidenceOrJsonListsRemainReadable() {
        MinutesRepository minutesRepository = mock(MinutesRepository.class);
        Minutes minutes = Minutes.builder().id("minutes-id").build();
        when(minutesRepository.findById("minutes-id")).thenReturn(Optional.of(minutes));
        MinutesService service = new MinutesService(
                minutesRepository, mock(ProjectRepository.class), mock(ProjectMemberRepository.class), mock(ClaudeService.class),
                mock(ProjectTodoService.class), mock(TodoProgressSyncService.class),
                mock(TranscriptionRepository.class), mock(MinutesStore.class));

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
                minutesRepository, projectRepository, mock(ProjectMemberRepository.class), claudeService,
                mock(ProjectTodoService.class), mock(TodoProgressSyncService.class),
                mock(TranscriptionRepository.class), mock(MinutesStore.class));

        MinutesRequest request = new MinutesRequest();
        request.setMeetingDate(LocalDate.now().toString());
        request.setRawText("conversation");

        assertThatThrownBy(() -> service.create("project-id", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
        verifyNoInteractions(claudeService);
        verify(minutesRepository, never()).save(any());
    }

    @Test
    void createRunsClaudeOutsideStoreAndReturnsExistingMinutesForSameRequestKey() {
        MinutesRepository minutesRepository = mock(MinutesRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ClaudeService claudeService = mock(ClaudeService.class);
        MinutesStore store = mock(MinutesStore.class);
        Project project = Project.builder().id("project-id").name("캡스톤").status(ProjectStatus.ACTIVE).build();
        when(projectRepository.findById("project-id")).thenReturn(Optional.of(project));
        when(claudeService.analyze(any(), any(), any(), any())).thenReturn(new ClaudeService.MinutesResult(
                "제목", "주제", List.of("논의"), List.of(), List.of(),
                List.of(new TodoData("박규남", "자료 조사", "미정")), List.of(), new EvidenceData()));
        when(store.saveAndSync(any(), any())).thenAnswer(invocation -> {
            Minutes minutes = invocation.getArgument(1);
            minutes.setId("minutes-1");
            return minutes;
        });
        MinutesService service = new MinutesService(
                minutesRepository, projectRepository, mock(ProjectMemberRepository.class), claudeService,
                mock(ProjectTodoService.class), mock(TodoProgressSyncService.class),
                mock(TranscriptionRepository.class), store);
        MinutesRequest request = new MinutesRequest();
        request.setMeetingDate("2026-10-01");
        request.setRawText("대화");

        MinutesResponse created = service.create("project-id", request, "retry-key-0001");

        assertThat(created.getId()).isEqualTo("minutes-1");
        assertThat(created.getTodos()).hasSize(1);
        assertThat(created.getTodos().get(0).getId()).isNotBlank();
        verify(store).saveAndSync(any(), any());

        Minutes existing = Minutes.builder().id("minutes-1").title("제목").build();
        when(minutesRepository.findByProjectIdAndRequestKey("project-id", "retry-key-0001"))
                .thenReturn(Optional.of(existing));
        MinutesResponse again = service.create("project-id", request, "retry-key-0001");

        assertThat(again.getId()).isEqualTo("minutes-1");
        verify(claudeService, org.mockito.Mockito.times(1)).analyze(any(), any(), any(), any());
    }

    @Test
    void mergeTodosKeepsKnownIdsAndAssignsNewOnes() {
        List<TodoData> existing = List.of(new TodoData("id-a", "A", "task a", "미정"), new TodoData("id-b", "B", "task b", "미정"));
        List<TodoItem> requested = List.of(
                new TodoItem("id-b", "B", "task b edited", "미정"),
                new TodoItem(null, "C", "new task", "미정"),
                new TodoItem("unknown", "D", "foreign id", "미정"));

        List<TodoData> merged = MinutesService.mergeTodos(existing, requested);

        assertThat(merged).extracting(TodoData::getTask).containsExactly("task b edited", "new task", "foreign id");
        assertThat(merged.get(0).getId()).isEqualTo("id-b");
        assertThat(merged.get(1).getId()).isNotBlank().isNotEqualTo("id-b");
        assertThat(merged.get(2).getId()).isNotEqualTo("unknown");
    }

    @Test
    void evidenceFollowsItemsAfterEdit() {
        List<String> oldItems = List.of("첫 번째 논의", "두 번째 논의", "세 번째 논의");
        List<String> oldEvidence = List.of("근거1", "근거2", "근거3");

        List<String> realigned = MinutesService.realignEvidence(
                oldItems, oldEvidence, List.of("세 번째 논의", "새 논의", "첫 번째  논의"));

        assertThat(realigned).containsExactly("근거3", "", "근거1");
    }

    @Test
    void deletingMinutesClearsIntegratedTodosAndTranscriptionLink() {
        MinutesRepository minutesRepository = mock(MinutesRepository.class);
        ProjectTodoService projectTodoService = mock(ProjectTodoService.class);
        TodoProgressSyncService progressSyncService = mock(TodoProgressSyncService.class);
        TranscriptionRepository transcriptionRepository = mock(TranscriptionRepository.class);
        Project project = Project.builder().id("project-id").build();
        Minutes minutes = Minutes.builder().id("minutes-1").project(project).transcriptionId("tr-1").build();
        Transcription transcription = Transcription.builder().id("tr-1").minutesId("minutes-1").build();
        when(minutesRepository.findById("minutes-1")).thenReturn(Optional.of(minutes));
        when(transcriptionRepository.findById("tr-1")).thenReturn(Optional.of(transcription));
        MinutesService service = new MinutesService(
                minutesRepository, mock(ProjectRepository.class), mock(ProjectMemberRepository.class), mock(ClaudeService.class),
                projectTodoService, progressSyncService, transcriptionRepository, mock(MinutesStore.class));

        assertThat(service.delete("project-id", "minutes-1")).isTrue();

        verify(progressSyncService).deleteByMinutes(minutes);
        verify(projectTodoService).deleteByMinutes(minutes);
        assertThat(transcription.getMinutesId()).isNull();
        verify(transcriptionRepository).save(transcription);
        verify(minutesRepository).delete(minutes);
    }
}
