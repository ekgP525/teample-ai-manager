package com.teample.service;

import com.teample.entity.Project;
import com.teample.entity.TranscriptSegment;
import com.teample.entity.Transcription;
import com.teample.entity.TranscriptionStatus;
import com.teample.repository.TranscriptionRepository;
import com.teample.service.transcription.MediaConverter;
import com.teample.service.transcription.MediaStorageService;
import com.teample.service.transcription.SpeechToTextProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TranscriptionProcessorTest {

    @Test
    void completesTranscriptionWhenProviderFinishes(@TempDir Path tempDir) throws Exception {
        MediaStorageService storage = new MediaStorageService(tempDir.toString());
        Files.createDirectories(tempDir.resolve("2026-10"));
        Files.write(tempDir.resolve("2026-10/a.mp3"), new byte[]{1});
        Transcription transcription = queued("2026-10/a.mp3");
        TranscriptionRepository repository = mock(TranscriptionRepository.class);
        when(repository.findById("tr-1")).thenReturn(Optional.of(transcription));
        when(repository.save(any(Transcription.class))).thenAnswer(inv -> inv.getArgument(0));
        SpeechToTextProvider provider = mock(SpeechToTextProvider.class);
        when(provider.name()).thenReturn("fake");
        when(provider.supportedExtensions()).thenReturn(Set.of("mp3"));
        when(provider.submit(any(), anyString(), any())).thenReturn("job-1");
        when(provider.poll("job-1"))
                .thenReturn(SpeechToTextProvider.PollResult.processing())
                .thenReturn(SpeechToTextProvider.PollResult.completed(
                        List.of(new TranscriptSegment("0", 0, 900, "안녕")), 900L));
        MediaConverter converter = mock(MediaConverter.class);
        when(converter.isAvailable()).thenReturn(false);

        new TranscriptionProcessor(repository, storage, converter, provider, 1, 1).process("tr-1");

        assertThat(transcription.getStatus()).isEqualTo(TranscriptionStatus.COMPLETED);
        assertThat(transcription.getProviderJobId()).isEqualTo("job-1");
        assertThat(transcription.getDurationMs()).isEqualTo(900L);
        assertThat(transcription.getSegmentsJson()).contains("\"text\":\"안녕\"");
        assertThat(transcription.getCompletedAt()).isNotNull();
        verify(converter, never()).extractAudio(any());
    }

    @Test
    void failsWhenFormatNeedsFfmpegButItIsMissing(@TempDir Path tempDir) throws Exception {
        MediaStorageService storage = new MediaStorageService(tempDir.toString());
        Files.createDirectories(tempDir.resolve("2026-10"));
        Files.write(tempDir.resolve("2026-10/a.webm"), new byte[]{1});
        Transcription transcription = queued("2026-10/a.webm");
        TranscriptionRepository repository = mock(TranscriptionRepository.class);
        when(repository.findById("tr-1")).thenReturn(Optional.of(transcription));
        when(repository.save(any(Transcription.class))).thenAnswer(inv -> inv.getArgument(0));
        SpeechToTextProvider provider = mock(SpeechToTextProvider.class);
        when(provider.supportedExtensions()).thenReturn(Set.of("mp3"));
        MediaConverter converter = mock(MediaConverter.class);
        when(converter.isAvailable()).thenReturn(false);

        new TranscriptionProcessor(repository, storage, converter, provider, 1, 1).process("tr-1");

        assertThat(transcription.getStatus()).isEqualTo(TranscriptionStatus.FAILED);
        assertThat(transcription.getErrorMessage()).contains("ffmpeg");
        verify(provider, never()).submit(any(), anyString(), any());
    }

    @Test
    void providerFailureIsRecorded(@TempDir Path tempDir) throws Exception {
        MediaStorageService storage = new MediaStorageService(tempDir.toString());
        Files.createDirectories(tempDir.resolve("2026-10"));
        Files.write(tempDir.resolve("2026-10/a.mp3"), new byte[]{1});
        Transcription transcription = queued("2026-10/a.mp3");
        transcription.setProviderJobId("job-9");
        transcription.setStatus(TranscriptionStatus.PROCESSING);
        TranscriptionRepository repository = mock(TranscriptionRepository.class);
        when(repository.findById("tr-1")).thenReturn(Optional.of(transcription));
        when(repository.save(any(Transcription.class))).thenAnswer(inv -> inv.getArgument(0));
        SpeechToTextProvider provider = mock(SpeechToTextProvider.class);
        when(provider.poll(eq("job-9"))).thenReturn(SpeechToTextProvider.PollResult.failed("quota exceeded"));

        new TranscriptionProcessor(repository, storage, mock(MediaConverter.class), provider, 1, 1).process("tr-1");

        assertThat(transcription.getStatus()).isEqualTo(TranscriptionStatus.FAILED);
        assertThat(transcription.getErrorMessage()).isEqualTo("quota exceeded");
        verify(provider, never()).submit(any(), anyString(), any());
    }

    private static Transcription queued(String storagePath) {
        return Transcription.builder()
                .id("tr-1")
                .project(Project.builder().id("project-1").name("p").build())
                .createdBy("user-1")
                .status(TranscriptionStatus.QUEUED)
                .storagePath(storagePath)
                .language("ko")
                .build();
    }
}
