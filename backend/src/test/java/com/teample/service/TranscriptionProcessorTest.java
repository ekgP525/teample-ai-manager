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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TranscriptionProcessorTest {

    private static final Executor DIRECT = Runnable::run;

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

        new TranscriptionProcessor(repository, storage, converter, provider, DIRECT, 1, 1).process("tr-1");

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

        new TranscriptionProcessor(repository, storage, converter, provider, DIRECT, 1, 1).process("tr-1");

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

        new TranscriptionProcessor(repository, storage, mock(MediaConverter.class), provider, DIRECT, 1, 1).process("tr-1");

        assertThat(transcription.getStatus()).isEqualTo(TranscriptionStatus.FAILED);
        assertThat(transcription.getErrorMessage()).isEqualTo("quota exceeded");
        verify(provider, never()).submit(any(), anyString(), any());
    }

    @Test
    void toleratesUpToFiveConsecutivePollFailures(@TempDir Path tempDir) throws Exception {
        MediaStorageService storage = new MediaStorageService(tempDir.toString());
        Transcription transcription = queued("2026-10/a.mp3");
        transcription.setProviderJobId("job-9");
        transcription.setStatus(TranscriptionStatus.PROCESSING);
        TranscriptionRepository repository = mock(TranscriptionRepository.class);
        when(repository.findById("tr-1")).thenReturn(Optional.of(transcription));
        when(repository.save(any(Transcription.class))).thenAnswer(inv -> inv.getArgument(0));
        SpeechToTextProvider provider = mock(SpeechToTextProvider.class);
        when(provider.poll("job-9"))
                .thenThrow(new SpeechToTextProvider.SpeechToTextException("502"))
                .thenThrow(new SpeechToTextProvider.SpeechToTextException("502"))
                .thenThrow(new SpeechToTextProvider.SpeechToTextException("502"))
                .thenThrow(new SpeechToTextProvider.SpeechToTextException("502"))
                .thenReturn(SpeechToTextProvider.PollResult.completed(List.of(new TranscriptSegment("0", 0, 1, "a")), 1L));

        new TranscriptionProcessor(repository, storage, mock(MediaConverter.class), provider, DIRECT, 1, 1).process("tr-1");

        assertThat(transcription.getStatus()).isEqualTo(TranscriptionStatus.COMPLETED);
        verify(provider, times(5)).poll("job-9");
    }

    @Test
    void failsAfterFiveConsecutivePollFailures(@TempDir Path tempDir) throws Exception {
        MediaStorageService storage = new MediaStorageService(tempDir.toString());
        Transcription transcription = queued("2026-10/a.mp3");
        transcription.setProviderJobId("job-9");
        transcription.setStatus(TranscriptionStatus.PROCESSING);
        TranscriptionRepository repository = mock(TranscriptionRepository.class);
        when(repository.findById("tr-1")).thenReturn(Optional.of(transcription));
        when(repository.save(any(Transcription.class))).thenAnswer(inv -> inv.getArgument(0));
        SpeechToTextProvider provider = mock(SpeechToTextProvider.class);
        when(provider.poll("job-9")).thenThrow(new SpeechToTextProvider.SpeechToTextException("STT down"));

        new TranscriptionProcessor(repository, storage, mock(MediaConverter.class), provider, DIRECT, 1, 1).process("tr-1");

        assertThat(transcription.getStatus()).isEqualTo(TranscriptionStatus.FAILED);
        assertThat(transcription.getErrorMessage()).isEqualTo("STT down");
        verify(provider, times(5)).poll("job-9");
    }

    @Test
    void interruptionLeavesRowProcessingInsteadOfFailed(@TempDir Path tempDir) throws Exception {
        MediaStorageService storage = new MediaStorageService(tempDir.toString());
        Transcription transcription = queued("2026-10/a.mp3");
        transcription.setProviderJobId("job-9");
        transcription.setStatus(TranscriptionStatus.PROCESSING);
        TranscriptionRepository repository = mock(TranscriptionRepository.class);
        when(repository.findById("tr-1")).thenReturn(Optional.of(transcription));
        when(repository.save(any(Transcription.class))).thenAnswer(inv -> inv.getArgument(0));
        SpeechToTextProvider provider = mock(SpeechToTextProvider.class);
        when(provider.poll("job-9")).thenThrow(new SpeechToTextProvider.SpeechToTextException(
                "전사 상태 조회이(가) 중단되었습니다.", new InterruptedException()));
        TranscriptionProcessor processor =
                new TranscriptionProcessor(repository, storage, mock(MediaConverter.class), provider, DIRECT, 1, 1);

        try {
            processor.process("tr-1");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }

        assertThat(transcription.getStatus()).isEqualTo(TranscriptionStatus.PROCESSING);
        verify(repository, never()).save(any());

        Thread.currentThread().interrupt();
        try {
            processor.process("tr-1");
        } finally {
            Thread.interrupted();
        }
        assertThat(transcription.getStatus()).isEqualTo(TranscriptionStatus.PROCESSING);
        verify(repository, never()).save(any());
    }

    @Test
    void doesNotWriteResultWhenRowWasDeletedMeanwhile(@TempDir Path tempDir) throws Exception {
        MediaStorageService storage = new MediaStorageService(tempDir.toString());
        Transcription transcription = queued("2026-10/a.mp3");
        transcription.setProviderJobId("job-9");
        transcription.setStatus(TranscriptionStatus.PROCESSING);
        TranscriptionRepository repository = mock(TranscriptionRepository.class);
        when(repository.findById("tr-1")).thenReturn(Optional.of(transcription)).thenReturn(Optional.empty());
        SpeechToTextProvider provider = mock(SpeechToTextProvider.class);
        when(provider.poll("job-9")).thenReturn(SpeechToTextProvider.PollResult.completed(
                List.of(new TranscriptSegment("0", 0, 1, "a")), 1L));

        new TranscriptionProcessor(repository, storage, mock(MediaConverter.class), provider, DIRECT, 1, 1).process("tr-1");

        verify(repository, never()).save(any());
    }

    @Test
    void copiesOnlyResultFieldsOntoFreshlyLoadedRow(@TempDir Path tempDir) throws Exception {
        MediaStorageService storage = new MediaStorageService(tempDir.toString());
        Transcription working = queued("2026-10/a.mp3");
        working.setProviderJobId("job-9");
        working.setStatus(TranscriptionStatus.PROCESSING);
        Transcription fresh = queued("2026-10/a.mp3");
        fresh.setProviderJobId("job-9");
        fresh.setStatus(TranscriptionStatus.PROCESSING);
        fresh.setSpeakerNamesJson("{\"0\":\"박규남\"}");
        TranscriptionRepository repository = mock(TranscriptionRepository.class);
        when(repository.findById("tr-1")).thenReturn(Optional.of(working)).thenReturn(Optional.of(fresh));
        List<Transcription> saved = new ArrayList<>();
        when(repository.save(any(Transcription.class))).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        SpeechToTextProvider provider = mock(SpeechToTextProvider.class);
        when(provider.poll("job-9")).thenReturn(SpeechToTextProvider.PollResult.completed(
                List.of(new TranscriptSegment("0", 0, 1, "a")), 1L));

        new TranscriptionProcessor(repository, storage, mock(MediaConverter.class), provider, DIRECT, 1, 1).process("tr-1");

        assertThat(saved).containsExactly(fresh);
        assertThat(fresh.getStatus()).isEqualTo(TranscriptionStatus.COMPLETED);
        assertThat(fresh.getSegmentsJson()).contains("\"text\":\"a\"");
        assertThat(fresh.getSpeakerNamesJson()).isEqualTo("{\"0\":\"박규남\"}");
    }

    @Test
    void resumeAndSweeperSubmitToExecutorAndSkipRunningIds(@TempDir Path tempDir) {
        MediaStorageService storage = new MediaStorageService(tempDir.toString());
        TranscriptionRepository repository = mock(TranscriptionRepository.class);
        Transcription a = queued("2026-10/a.mp3");
        Transcription b = queued("2026-10/b.mp3");
        b.setId("tr-2");
        when(repository.findByStatusIn(any())).thenReturn(List.of(a, b));
        when(repository.findByStatusAndProviderJobIdIsNullAndCreatedAtBefore(eq(TranscriptionStatus.QUEUED), any(LocalDateTime.class)))
                .thenReturn(List.of(a, b));
        List<Runnable> submitted = new ArrayList<>();
        AtomicInteger rejections = new AtomicInteger();
        Executor executor = task -> {
            if (submitted.size() >= 3) {
                rejections.incrementAndGet();
                throw new RejectedExecutionException("full");
            }
            submitted.add(task);
        };
        TranscriptionProcessor processor = new TranscriptionProcessor(
                repository, storage, mock(MediaConverter.class), mock(SpeechToTextProvider.class), executor, 1, 1);

        processor.resumeUnfinished();
        assertThat(submitted).hasSize(2);

        processor.redispatchStaleQueued();
        assertThat(submitted).hasSize(3);
        assertThat(rejections.get()).isEqualTo(1);
        verify(repository, never()).findById(anyString());
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
