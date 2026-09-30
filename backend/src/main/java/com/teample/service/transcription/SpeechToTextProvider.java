package com.teample.service.transcription;

import com.teample.entity.TranscriptSegment;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * 외부 STT 서비스 추상화. 제공자를 바꿔도 TranscriptionService는 그대로 둔다.
 */
public interface SpeechToTextProvider {

    String name();

    /** 자격 증명이 없으면 false. 이때 전사 요청은 503으로 거절한다. */
    boolean isConfigured();

    /** 변환 없이 바로 보낼 수 있는 파일 확장자(소문자, 점 없음). */
    Set<String> supportedExtensions();

    /** 파일을 제출하고 제공자 측 작업 ID를 돌려준다. */
    String submit(Path file, String fileName, TranscriptionOptions options) throws SpeechToTextException;

    /** 작업 상태를 조회한다. 완료 시 세그먼트를 포함한다. */
    PollResult poll(String jobId) throws SpeechToTextException;

    record TranscriptionOptions(String language, Integer expectedSpeakers) {
    }

    enum PollState {
        PROCESSING,
        COMPLETED,
        FAILED
    }

    record PollResult(PollState state, List<TranscriptSegment> segments, Long durationMs, String error) {
        public static PollResult processing() {
            return new PollResult(PollState.PROCESSING, List.of(), null, null);
        }

        public static PollResult failed(String error) {
            return new PollResult(PollState.FAILED, List.of(), null, error);
        }

        public static PollResult completed(List<TranscriptSegment> segments, Long durationMs) {
            return new PollResult(PollState.COMPLETED, segments, durationMs, null);
        }
    }

    class SpeechToTextException extends Exception {
        public SpeechToTextException(String message) {
            super(message);
        }

        public SpeechToTextException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
