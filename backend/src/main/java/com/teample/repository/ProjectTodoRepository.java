package com.teample.repository;

import com.teample.entity.ProjectTodo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectTodoRepository extends JpaRepository<ProjectTodo, String> {
    List<ProjectTodo> findByProjectId(String projectId);
    List<ProjectTodo> findByMinutesId(String minutesId);
    Optional<ProjectTodo> findByProjectIdAndMinutesIdAndSourceIndex(String projectId, String minutesId, Integer sourceIndex);
    void deleteByMinutesId(String minutesId);
    void deleteByProjectId(String projectId);
}