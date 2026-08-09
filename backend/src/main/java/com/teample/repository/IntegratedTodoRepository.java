package com.teample.repository;

import com.teample.entity.IntegratedTodo;
import com.teample.entity.TodoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IntegratedTodoRepository extends JpaRepository<IntegratedTodo, String> {
    List<IntegratedTodo> findByProjectIdAndStatusOrderByPriorityOrderAscCreatedAtAsc(
            String projectId, TodoStatus status);

    List<IntegratedTodo> findByMinutesIdOrderBySourceIndexAsc(String minutesId);

    Optional<IntegratedTodo> findByIdAndProjectId(String id, String projectId);

    @Query("select coalesce(max(t.priorityOrder), 0) from IntegratedTodo t where t.project.id = :projectId")
    int findMaxPriorityOrderByProjectId(@Param("projectId") String projectId);
}
