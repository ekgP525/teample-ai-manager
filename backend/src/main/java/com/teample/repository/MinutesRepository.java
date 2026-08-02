package com.teample.repository;

import com.teample.entity.Minutes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MinutesRepository extends JpaRepository<Minutes, String> {
    List<Minutes> findByProjectIdOrderByCreatedAtDesc(String projectId);
}
