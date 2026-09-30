package com.teample.dto.transcription;

import com.teample.entity.TranscriptSegment;
import com.teample.entity.TranscriptionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record TranscriptionResponse(
        String id,
        String projectId,
        TranscriptionStatus status,
        String sourceFileName,
        String mediaKind,
        Long durationMs,
        Integer expectedSpeakers,
        List<String> speakerLabels,
        Map<String, String> speakerNames,
        List<TranscriptSegment> segments,
        String errorMessage,
        String minutesId,
        boolean hasAudio,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
