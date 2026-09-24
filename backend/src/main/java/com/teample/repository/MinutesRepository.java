package com.teample.repository;

import com.teample.entity.Minutes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MinutesRepository extends JpaRepository<Minutes, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from Minutes m where m.id = :id")
    java.util.Optional<Minutes> findLockedById(@org.springframework.data.repository.query.Param("id") String id);

    List<Minutes> findByProjectIdOrderByCreatedAtDesc(String projectId);
    void deleteByProjectId(String projectId);
}
