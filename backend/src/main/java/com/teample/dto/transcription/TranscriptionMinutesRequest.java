package com.teample.dto.transcription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TranscriptionMinutesRequest(
        @Size(max = 255) String title,
        @NotBlank String meetingDate
) {
}
