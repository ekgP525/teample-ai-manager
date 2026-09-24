package com.teample.repository;

import com.teample.entity.Project;
import com.teample.entity.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Project p where p.id = :id")
    java.util.Optional<Project> findLockedById(@org.springframework.data.repository.query.Param("id") String id);

    List<Project> findByStatus(ProjectStatus status);
}
