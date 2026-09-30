package com.teample.entity;

public enum TranscriptionStatus {
    /** 파일 저장 완료, 처리 대기 */
    QUEUED,
    /** 변환 또는 외부 STT 처리 중 */
    PROCESSING,
    /** 전사 완료, 화자 매핑과 회의록 생성 가능 */
    COMPLETED,
    /** 처리 실패 */
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
