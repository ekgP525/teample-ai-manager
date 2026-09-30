package com.teample.repository;

import com.teample.entity.Transcription;
import com.teample.entity.TranscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TranscriptionRepository extends JpaRepository<Transcription, String> {

    List<Transcription> findByProjectIdOrderByCreatedAtDesc(String projectId);

    List<Transcription> findByStatusIn(Collection<TranscriptionStatus> statuses);

    long countByCreatedByAndStatusIn(String createdBy, Collection<TranscriptionStatus> statuses);

    /** 기간 내 사용자가 만든 전사의 총 길이(ms). 실패한 작업은 제외한다. */
    @Query("select coalesce(sum(t.durationMs), 0) from Transcription t "
            + "where t.createdBy = :userId and t.createdAt >= :since and t.status <> com.teample.entity.TranscriptionStatus.FAILED")
    long sumDurationMsByCreatedBySince(@Param("userId") String userId, @Param("since") LocalDateTime since);

    void deleteByProjectId(String projectId);
}
