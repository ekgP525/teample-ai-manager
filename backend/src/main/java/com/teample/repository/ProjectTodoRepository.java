package com.teample.repository;

import com.teample.entity.ProjectTodo;
import com.teample.entity.TodoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectTodoRepository extends JpaRepository<ProjectTodo, String> {
    List<ProjectTodo> findByProjectIdAndStatusOrderByPriorityOrderAscCreatedAtAsc(
            String projectId, TodoStatus status);

    List<ProjectTodo> findByMinutesIdOrderBySourceIndexAsc(String minutesId);

    Optional<ProjectTodo> findByIdAndProjectId(String id, String projectId);

    @Query("select coalesce(max(t.priorityOrder), 0) from ProjectTodo t where t.project.id = :projectId")
    int findMaxPriorityOrderByProjectId(@Param("projectId") String projectId);
}
