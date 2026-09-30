package com.teample.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teample.config.AsyncConfig;
import com.teample.entity.Transcription;
import com.teample.entity.TranscriptionStatus;
import com.teample.repository.TranscriptionRepository;
import com.teample.service.transcription.MediaConverter;
import com.teample.service.transcription.MediaStorageService;
import com.teample.service.transcription.SpeechToTextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 전사 한 건을 백그라운드에서 끝까지 처리한다.
 * 트랜잭션을 길게 잡지 않도록 상태 변경마다 짧게 저장한다.
 */
@Service
public class TranscriptionProcessor {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionProcessor.class);

    private final TranscriptionRepository transcriptionRepository;
    private final MediaStorageService mediaStorageService;
    private final MediaConverter mediaConverter;
    private final SpeechToTextProvider provider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Duration pollInterval;
    private final Duration maxWait;

    public TranscriptionProcessor(
            TranscriptionRepository transcriptionRepository,
            MediaStorageService mediaStorageService,
            MediaConverter mediaConverter,
            SpeechToTextProvider provider,
            @Value("${stt.poll-interval-seconds:5}") long pollIntervalSeconds,
            @Value("${stt.max-wait-minutes:120}") long maxWaitMinutes
    ) {
        this.transcriptionRepository = transcriptionRepository;
        this.mediaStorageService = mediaStorageService;
        this.mediaConverter = mediaConverter;
        this.provider = provider;
        this.pollInterval = Duration.ofSeconds(Math.max(pollIntervalSeconds, 1));
        this.maxWait = Duration.ofMinutes(Math.max(maxWaitMinutes, 1));
    }

    @Async(AsyncConfig.TRANSCRIPTION_EXECUTOR)
    public void process(String transcriptionId) {
        Transcription transcription = transcriptionRepository.findById(transcriptionId).orElse(null);
        if (transcription == null || transcription.getStatus().isTerminal()) {
            return;
        }
        try {
            String jobId = transcription.getProviderJobId();
            if (jobId == null || jobId.isBlank()) {
                jobId = submit(transcription);
            } else {
                markProcessing(transcription);
            }
            waitForResult(transcription, jobId);
        } catch (SpeechToTextProvider.SpeechToTextException | IOException e) {
            fail(transcription, e.getMessage());
        } catch (RuntimeException e) {
            log.error("전사 처리 중 예기치 않은 오류 (id={})", transcriptionId, e);
            fail(transcription, "전사 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /** 서버가 재시작되면 끝나지 않은 작업을 다시 이어간다. */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeUnfinished() {
        List<Transcription> unfinished = transcriptionRepository.findByStatusIn(
                List.of(TranscriptionStatus.QUEUED, TranscriptionStatus.PROCESSING));
        if (unfinished.isEmpty()) {
            return;
        }
        log.info("끝나지 않은 전사 {}건을 다시 처리합니다.", unfinished.size());
        unfinished.forEach(transcription -> process(transcription.getId()));
    }

    private String submit(Transcription transcription) throws SpeechToTextProvider.SpeechToTextException, IOException {
        markProcessing(transcription);
        Path source = mediaStorageService.resolve(transcription.getStoragePath());
        if (!Files.isRegularFile(source)) {
            throw new IOException("업로드된 파일을 찾을 수 없습니다.");
        }
        Path upload = source;
        String extension = extensionOf(source);
        if (!provider.supportedExtensions().contains(extension)) {
            if (!mediaConverter.isAvailable()) {
                throw new IOException("이 파일 형식(" + extension + ")은 서버에 ffmpeg가 설치되어야 처리할 수 있습니다. mp3, m4a, wav, mp4 형식으로 올려 주세요.");
            }
            upload = mediaConverter.extractAudio(source);
        }
        if (transcription.getDurationMs() == null && mediaConverter.isAvailable()) {
            Long probed = mediaConverter.probeDurationMs(upload);
            if (probed != null) {
                transcription.setDurationMs(probed);
            }
        }
        String jobId = provider.submit(upload, upload.getFileName().toString(),
                new SpeechToTextProvider.TranscriptionOptions(transcription.getLanguage(), transcription.getExpectedSpeakers()));
        transcription.setProviderJobId(jobId);
        transcription.setProvider(provider.name());
        transcriptionRepository.save(transcription);
        if (!upload.equals(source)) {
            try {
                Files.deleteIfExists(upload);
            } catch (IOException ignored) {
                // 변환 임시 파일 삭제 실패는 무시한다.
            }
        }
        return jobId;
    }

    private void waitForResult(Transcription transcription, String jobId)
            throws SpeechToTextProvider.SpeechToTextException {
        Instant deadline = Instant.now().plus(maxWait);
        while (Instant.now().isBefore(deadline)) {
            sleep();
            SpeechToTextProvider.PollResult result = provider.poll(jobId);
            switch (result.state()) {
                case COMPLETED -> {
                    complete(transcription, result);
                    return;
                }
                case FAILED -> {
                    fail(transcription, result.error() == null ? "STT 처리에 실패했습니다." : result.error());
                    return;
                }
                default -> {
                    // 계속 대기
                }
            }
        }
        fail(transcription, "전사 대기 시간이 초과되었습니다. 파일 길이를 줄여 다시 시도해 주세요.");
    }

    private void complete(Transcription transcription, SpeechToTextProvider.PollResult result) {
        try {
            transcription.setSegmentsJson(objectMapper.writeValueAsString(result.segments()));
        } catch (IOException e) {
            fail(transcription, "전사 결과를 저장하지 못했습니다.");
            return;
        }
        if (result.durationMs() != null && (transcription.getDurationMs() == null || transcription.getDurationMs() <= 0)) {
            transcription.setDurationMs(result.durationMs());
        }
        transcription.setStatus(TranscriptionStatus.COMPLETED);
        transcription.setErrorMessage(null);
        transcription.setCompletedAt(LocalDateTime.now());
        transcriptionRepository.save(transcription);
    }

    private void fail(Transcription transcription, String message) {
        transcription.setStatus(TranscriptionStatus.FAILED);
        transcription.setErrorMessage(message == null ? "알 수 없는 오류" : truncate(message, 2000));
        transcription.setCompletedAt(LocalDateTime.now());
        transcriptionRepository.save(transcription);
    }

    private void markProcessing(Transcription transcription) {
        if (transcription.getStatus() != TranscriptionStatus.PROCESSING) {
            transcription.setStatus(TranscriptionStatus.PROCESSING);
            transcriptionRepository.save(transcription);
        }
    }

    private void sleep() {
        try {
            Thread.sleep(pollInterval.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
