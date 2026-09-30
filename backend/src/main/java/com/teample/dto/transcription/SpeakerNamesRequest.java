package com.teample.dto.transcription;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/** STT 화자 라벨("1", "2", ...) → 표시 이름 매핑. 값이 비면 그 라벨은 매핑을 지운다. */
public record SpeakerNamesRequest(@NotNull Map<String, String> speakerNames) {
}
