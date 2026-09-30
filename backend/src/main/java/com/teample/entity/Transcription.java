package com.teample.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 음성·영상 파일 하나에 대한 전사 작업. 파일 업로드부터 STT 완료, 회의록 생성까지의 상태를 담는다.
 */
@Entity
@Table(name = "transcriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transcription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TranscriptionStatus status;

    @Column(name = "source_file_name", length = 500)
    private String sourceFileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    /** AUDIO 또는 VIDEO */
    @Column(name = "media_kind", length = 32)
    private String mediaKind;

    /** 서버 저장소 내 상대 경로 */
    @Column(name = "storage_path", length = 1000)
    private String storagePath;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(length = 64)
    private String provider;

    @Column(name = "provider_job_id")
    private String providerJobId;

    @Column(length = 16)
    private String language;

    @Column(name = "expected_speakers")
    private Integer expectedSpeakers;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "segments", columnDefinition = "text")
    private String segmentsJson;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "speaker_names", columnDefinition = "text")
    private String speakerNamesJson;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "minutes_id")
    private String minutesId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean belongsToProject(String projectId) {
        return project != null && project.getId() != null && project.getId().equals(projectId);
    }

    public boolean hasMinutes() {
        return minutesId != null && !minutesId.isBlank();
    }
}
