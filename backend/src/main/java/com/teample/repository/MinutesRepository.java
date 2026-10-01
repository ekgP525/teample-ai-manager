package com.teample.repository;

import com.teample.entity.Minutes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MinutesRepository extends JpaRepository<Minutes, String> {
    List<Minutes> findByProjectIdOrderByCreatedAtDesc(String projectId);
    Optional<Minutes> findByProjectIdAndRequestKey(String projectId, String requestKey);
    void deleteByProjectId(String projectId);
}
