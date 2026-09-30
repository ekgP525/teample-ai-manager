package com.teample.service;

import com.teample.dto.MinutesResponse;
import com.teample.dto.transcription.TranscriptionResponse;
import com.teample.entity.Project;
import com.teample.entity.ProjectStatus;
import com.teample.entity.TranscriptSegment;
import com.teample.entity.Transcription;
import com.teample.entity.TranscriptionStatus;
import com.teample.repository.ProjectRepository;
import com.teample.repository.TranscriptionRepository;
import com.teample.security.AuthenticatedUser;
import com.teample.service.transcription.MediaStorageService;
import com.teample.service.transcription.SpeechToTextProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TranscriptionServiceTest {

    private final AuthenticatedUser user = new AuthenticatedUser("user-1", "박규남", null);

    @Test
    void uploadIsRejectedWhenProviderIsNotConfigured(@TempDir Path tempDir) {
        Fixture fixture = new Fixture(tempDir, false);
        MockMultipartFile file = new MockMultipartFile("file", "meeting.mp3", "audio/mpeg", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> fixture.service.create("project-1", user, false, file, 3, "ko", true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503");
        verify(fixture.transcriptionRepository, never()).save(any());
    }

    @Test
    void uploadRequiresConsentAndSupportedExtension(@TempDir Path tempDir) {
        Fixture fixture = new Fixture(tempDir, true);
        MockMultipartFile mp3 = new MockMultipartFile("file", "meeting.mp3", "audio/mpeg", new byte[]{1});
        MockMultipartFile pdf = new MockMultipartFile("file", "notes.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> fixture.service.create("project-1", user, false, mp3, null, null, false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> fixture.service.create("project-1", user, false, pdf, null, null, true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("지원하지 않는 파일 형식");
    }

    @Test
    void uploadStoresFileAndQueuesProcessing(@TempDir Path tempDir) throws Exception {
        Fixture fixture = new Fixture(tempDir, true);
        MockMultipartFile file = new MockMultipartFile("file", "회의.mp4", "video/mp4", new byte[]{9, 9, 9});
        when(fixture.transcriptionRepository.save(any(Transcription.class))).thenAnswer(invocation -> {
            Transcription saved = invocation.getArgument(0);
            saved.setId("tr-1");
            return saved;
        });

        TranscriptionResponse response = fixture.service.create("project-1", user, false, file, 4, null, true);

        assertThat(response.status()).isEqualTo(TranscriptionStatus.QUEUED);
        assertThat(response.mediaKind()).isEqualTo("VIDEO");
        assertThat(response.expectedSpeakers()).isEqualTo(4);
        assertThat(response.hasAudio()).isTrue();
        try (var files = Files.walk(tempDir)) {
            assertThat(files.filter(Files::isRegularFile).count()).isEqualTo(1);
        }
        verify(fixture.processor).process("tr-1");
    }

    @Test
    void transcriptTextUsesSpeakerNamesAndTimestamps(@TempDir Path tempDir) {
        Fixture fixture = new Fixture(tempDir, true);
        List<TranscriptSegment> segments = List.of(
                new TranscriptSegment("0", 0, 1500, "오늘 회의 시작할게요"),
                new TranscriptSegment("1", 65_000, 70_000, "네 좋아요")
        );

        String text = fixture.service.buildTranscriptText(segments, Map.of("0", "박규남"));

        assertThat(text).isEqualTo("[00:00] 박규남: 오늘 회의 시작할게요\n[01:05] 화자 1: 네 좋아요");
        assertThat(TranscriptionService.formatTimestamp(3_725_000)).isEqualTo("1:02:05");
    }

    @Test
    void creatingMinutesRequiresCompletedTranscriptionAndLinksResult(@TempDir Path tempDir) {
        Fixture fixture = new Fixture(tempDir, true);
        Transcription transcription = Transcription.builder()
                .id("tr-1").project(fixture.project).createdBy("user-1")
                .status(TranscriptionStatus.COMPLETED)
                .segmentsJson("[{\"speaker\":\"0\",\"startMs\":0,\"endMs\":1000,\"text\":\"발표 자료는 다혜가 맡기로\"}]")
                .speakerNamesJson("{\"0\":\"박규남\"}")
                .build();
        when(fixture.transcriptionRepository.findById("tr-1")).thenReturn(Optional.of(transcription));
        when(fixture.transcriptionRepository.save(any(Transcription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fixture.minutesService.createFromTranscript(eq(fixture.project), eq("1차 회의"), eq(LocalDate.of(2026, 10, 1)),
                eq("[00:00] 박규남: 발표 자료는 다혜가 맡기로"), eq("tr-1")))
                .thenReturn(MinutesResponse.builder().id("minutes-1").build());

        MinutesResponse minutes = fixture.service
                .createMinutes("project-1", "tr-1", "1차 회의", "2026-10-01", user, false)
                .orElseThrow();

        assertThat(minutes.getId()).isEqualTo("minutes-1");
        assertThat(transcription.getMinutesId()).isEqualTo("minutes-1");

        assertThatThrownBy(() -> fixture.service.createMinutes("project-1", "tr-1", null, "2026-10-01", user, false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("이미 있습니다");
    }

    @Test
    void speakerNamesAreCleanedBeforeSaving(@TempDir Path tempDir) {
        Fixture fixture = new Fixture(tempDir, true);
        Transcription transcription = Transcription.builder()
                .id("tr-1").project(fixture.project).createdBy("user-1")
                .status(TranscriptionStatus.COMPLETED)
                .segmentsJson("[{\"speaker\":\"0\",\"startMs\":0,\"endMs\":1000,\"text\":\"a\"},"
                        + "{\"speaker\":\"1\",\"startMs\":1000,\"endMs\":2000,\"text\":\"b\"}]")
                .build();
        when(fixture.transcriptionRepository.findById("tr-1")).thenReturn(Optional.of(transcription));
        when(fixture.transcriptionRepository.save(any(Transcription.class))).thenAnswer(inv -> inv.getArgument(0));

        TranscriptionResponse response = fixture.service.updateSpeakerNames(
                "project-1", "tr-1", Map.of("0", " 박규남 ", "1", "  ", "9", "없는화자"), user, false).orElseThrow();

        assertThat(response.speakerNames()).containsExactlyEntriesOf(Map.of("0", "박규남", "9", "없는화자"));
        assertThat(response.speakerLabels()).containsExactly("0", "1");
    }

    private static final class Fixture {
        final TranscriptionRepository transcriptionRepository = mock(TranscriptionRepository.class);
        final ProjectRepository projectRepository = mock(ProjectRepository.class);
        final ProjectMemberService projectMemberService = mock(ProjectMemberService.class);
        final PlanService planService = mock(PlanService.class);
        final SpeechToTextProvider provider = mock(SpeechToTextProvider.class);
        final TranscriptionProcessor processor = mock(TranscriptionProcessor.class);
        final MinutesService minutesService = mock(MinutesService.class);
        final Project project = Project.builder().id("project-1").name("캡스톤").status(ProjectStatus.ACTIVE).build();
        final TranscriptionService service;

        Fixture(Path tempDir, boolean configured) {
            when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
            when(provider.isConfigured()).thenReturn(configured);
            when(provider.name()).thenReturn("fake");
            when(provider.supportedExtensions()).thenReturn(Set.of("mp3", "mp4"));
            service = new TranscriptionService(
                    transcriptionRepository, projectRepository, projectMemberService, planService,
                    new MediaStorageService(tempDir.toString()), provider, processor, minutesService, 100);
        }
    }
}
