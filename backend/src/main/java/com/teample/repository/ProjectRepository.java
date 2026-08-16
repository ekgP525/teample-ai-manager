package com.teample.repository;

import com.teample.entity.Project;
import com.teample.entity.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, String> {
    List<Project> findByStatus(ProjectStatus status);
}
