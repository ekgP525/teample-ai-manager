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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 전사 한 건을 백그라운드에서 끝까지 처리한다.
 * 트랜잭션을 길게 잡지 않도록 상태 변경마다 짧게 저장하고, 저장 직전에 행을 다시 읽어 지워진 행에는 쓰지 않는다.
 */
@Service
public class TranscriptionProcessor {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionProcessor.class);
    static final int MAX_CONSECUTIVE_POLL_FAILURES = 5;
    static final Duration STALE_QUEUED_AGE = Duration.ofMinutes(2);

    private final TranscriptionRepository transcriptionRepository;
    private final MediaStorageService mediaStorageService;
    private final MediaConverter mediaConverter;
    private final SpeechToTextProvider provider;
    private final Executor executor;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Duration pollInterval;
    private final Duration maxWait;
    /** 지금 이 인스턴스에서 처리 중인 전사 ID. 재디스패치가 같은 작업을 두 번 돌리지 않게 막는다. */
    private final Set<String> running = ConcurrentHashMap.newKeySet();
    private final long premiumMonthlyMinutes;

    public TranscriptionProcessor(
            TranscriptionRepository transcriptionRepository,
            MediaStorageService mediaStorageService,
            MediaConverter mediaConverter,
            SpeechToTextProvider provider,
            @Qualifier(AsyncConfig.TRANSCRIPTION_EXECUTOR) Executor executor,
            @Value("${stt.poll-interval-seconds:5}") long pollIntervalSeconds,
            @Value("${stt.max-wait-minutes:120}") long maxWaitMinutes,
            @Value("${app.plan.premium-monthly-minutes:600}") long premiumMonthlyMinutes
    ) {
        this.premiumMonthlyMinutes = premiumMonthlyMinutes;
        this.transcriptionRepository = transcriptionRepository;
        this.mediaStorageService = mediaStorageService;
        this.mediaConverter = mediaConverter;
        this.provider = provider;
        this.executor = executor;
        this.pollInterval = Duration.ofSeconds(Math.max(pollIntervalSeconds, 1));
        this.maxWait = Duration.ofMinutes(Math.max(maxWaitMinutes, 1));
    }

    @Async(AsyncConfig.TRANSCRIPTION_EXECUTOR)
    public void process(String transcriptionId) {
        if (!running.add(transcriptionId)) {
            log.debug("전사 {}는 이미 처리 중이라 건너뜁니다.", transcriptionId);
            return;
        }
        try {
            processInternal(transcriptionId);
        } finally {
            running.remove(transcriptionId);
        }
    }

    private void processInternal(String transcriptionId) {
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
        } catch (InterruptedException e) {
            // 서버 종료 등으로 끊긴 것이다. FAILED로 만들지 않고 PROCESSING으로 둬서 재시작 시 이어가게 한다.
            Thread.currentThread().interrupt();
            log.info("전사 {} 처리가 중단되었습니다. 재시작 후 이어서 처리합니다.", transcriptionId);
        } catch (SpeechToTextProvider.SpeechToTextException e) {
            if (isInterruption(e)) {
                Thread.currentThread().interrupt();
                log.info("전사 {} 처리가 중단되었습니다. 재시작 후 이어서 처리합니다.", transcriptionId);
                return;
            }
            fail(transcription, e.getMessage());
        } catch (IOException e) {
            fail(transcription, e.getMessage());
        } catch (RuntimeException e) {
            log.error("전사 처리 중 예기치 않은 오류 (id={})", transcriptionId, e);
            fail(transcription, "전사 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /** 서버가 재시작되면 끝나지 않은 작업을 다시 이어간다. 실행기에 직접 넣어 자기 호출(@Async 무시)을 피한다. */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeUnfinished() {
        List<Transcription> unfinished = transcriptionRepository.findByStatusIn(
                List.of(TranscriptionStatus.QUEUED, TranscriptionStatus.PROCESSING));
        if (unfinished.isEmpty()) {
            return;
        }
        log.info("끝나지 않은 전사 {}건을 다시 처리합니다.", unfinished.size());
        unfinished.forEach(transcription -> dispatch(transcription.getId()));
    }

    /**
     * 큐에 들어갔지만 실행기에 거절되는 등 2분 넘게 시작되지 않은 작업을 5분마다 다시 넣는다.
     * process()는 끝난 행이면 바로 돌아오고 running 집합으로 중복 실행도 막으므로 여러 번 불러도 안전하다.
     */
    @Scheduled(fixedDelayString = "${stt.sweep-interval-ms:300000}", initialDelayString = "${stt.sweep-interval-ms:300000}")
    public void redispatchStaleQueued() {
        LocalDateTime threshold = LocalDateTime.now().minus(STALE_QUEUED_AGE);
        List<Transcription> stale = transcriptionRepository
                .findByStatusAndProviderJobIdIsNullAndCreatedAtBefore(TranscriptionStatus.QUEUED, threshold);
        for (Transcription transcription : stale) {
            if (running.contains(transcription.getId())) {
                continue;
            }
            log.info("시작되지 않은 전사 {}를 다시 넣습니다.", transcription.getId());
            dispatch(transcription.getId());
        }
    }

    boolean isRunning(String transcriptionId) {
        return running.contains(transcriptionId);
    }

    private void dispatch(String transcriptionId) {
        try {
            executor.execute(() -> process(transcriptionId));
        } catch (RejectedExecutionException e) {
            log.warn("전사 실행기가 가득 차서 {}를 넣지 못했습니다. 다음 점검 때 다시 시도합니다.", transcriptionId);
        }
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
        try {
            if (transcription.getDurationMs() == null && mediaConverter.isAvailable()) {
                Long probed = mediaConverter.probeDurationMs(upload);
                if (probed != null) {
                    transcription.setDurationMs(probed);
                }
            }
            ensureWithinMonthlyQuota(transcription);
            String jobId = provider.submit(upload, upload.getFileName().toString(),
                    new SpeechToTextProvider.TranscriptionOptions(transcription.getLanguage(), transcription.getExpectedSpeakers()));
            transcription.setProviderJobId(jobId);
            saveFresh(transcription);
            return jobId;
        } finally {
            if (!upload.equals(source)) {
                try {
                    Files.deleteIfExists(upload);
                } catch (IOException ignored) {
                    // 변환 임시 파일 삭제 실패는 무시한다.
                }
            }
        }
    }

    /**
     * 길이를 미리 알 수 있는 경우(ffprobe) STT를 보내기 전에 월 한도를 넘는지 확인한다.
     * 업로드 시점 검사는 길이를 몰라 통과시키므로, 여기서 한 번 더 막아 1분 남은 사용자가 수 시간짜리를 쓰는 걸 방지한다.
     */
    private void ensureWithinMonthlyQuota(Transcription transcription) throws IOException {
        Long durationMs = transcription.getDurationMs();
        String userId = transcription.getCreatedBy();
        if (durationMs == null || durationMs <= 0 || userId == null || userId.startsWith("admin-test:")) {
            return;
        }
        LocalDateTime monthStart = AppClock.today().withDayOfMonth(1).atStartOfDay();
        long usedMs = transcriptionRepository.sumDurationMsByCreatedBySince(userId, monthStart);
        // 자기 자신의 길이는 이미 저장돼 합계에 포함돼 있을 수 있으므로 빼고 계산한다.
        long othersMs = Math.max(0, usedMs - durationMs);
        long limitMs = premiumMonthlyMinutes * 60_000L;
        if (othersMs + durationMs > limitMs) {
            long remainingMinutes = Math.max(0, (limitMs - othersMs) / 60_000L);
            throw new IOException("이번 달 남은 전사 시간(" + remainingMinutes + "분)보다 긴 파일입니다. 더 짧은 파일을 올리거나 다음 달에 다시 시도해 주세요.");
        }
    }

    private void waitForResult(Transcription transcription, String jobId)
            throws SpeechToTextProvider.SpeechToTextException, InterruptedException {
        Instant deadline = Instant.now().plus(maxWait);
        int consecutiveFailures = 0;
        while (Instant.now().isBefore(deadline)) {
            sleep();
            SpeechToTextProvider.PollResult result;
            try {
                result = provider.poll(jobId);
                consecutiveFailures = 0;
            } catch (SpeechToTextProvider.SpeechToTextException e) {
                if (isInterruption(e)) {
                    throw e;
                }
                consecutiveFailures++;
                if (consecutiveFailures >= MAX_CONSECUTIVE_POLL_FAILURES) {
                    throw e;
                }
                log.warn("전사 상태 조회 실패 {}회 (id={}): {}", consecutiveFailures, transcription.getId(), e.getMessage());
                continue;
            }
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
        saveFresh(transcription);
    }

    private void fail(Transcription transcription, String message) {
        transcription.setStatus(TranscriptionStatus.FAILED);
        transcription.setErrorMessage(message == null ? "알 수 없는 오류" : truncate(message, 2000));
        transcription.setCompletedAt(LocalDateTime.now());
        saveFresh(transcription);
    }

    private void markProcessing(Transcription transcription) {
        if (transcription.getStatus() != TranscriptionStatus.PROCESSING) {
            transcription.setStatus(TranscriptionStatus.PROCESSING);
            saveFresh(transcription);
        }
    }

    /**
     * 작업 중 들고 있던 객체를 그대로 저장하지 않고 행을 다시 읽어 결과 필드만 옮겨 쓴다.
     * 그 사이 행이 지워졌으면 조용히 돌아온다.
     */
    private void saveFresh(Transcription working) {
        Transcription fresh = transcriptionRepository.findById(working.getId()).orElse(null);
        if (fresh == null) {
            log.info("전사 {}가 처리 중 삭제되어 결과를 저장하지 않습니다.", working.getId());
            return;
        }
        if (fresh != working) {
            fresh.setStatus(working.getStatus());
            fresh.setSegmentsJson(working.getSegmentsJson());
            fresh.setDurationMs(working.getDurationMs());
            fresh.setErrorMessage(working.getErrorMessage());
            fresh.setCompletedAt(working.getCompletedAt());
            fresh.setProviderJobId(working.getProviderJobId());
        }
        transcriptionRepository.save(fresh);
    }

    private void sleep() throws InterruptedException {
        Thread.sleep(pollInterval.toMillis());
    }

    private static boolean isInterruption(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return Thread.currentThread().isInterrupted();
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
