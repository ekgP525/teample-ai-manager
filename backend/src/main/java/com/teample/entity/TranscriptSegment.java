package com.teample.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 전사 결과의 발언 한 단위. speaker는 STT가 부여한 익명 라벨("1", "2" 등)이며
 * 실제 이름 매핑은 Transcription.speakerNames에 따로 저장한다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptSegment {
    private String speaker;
    private long startMs;
    private long endMs;
    private String text;
}
