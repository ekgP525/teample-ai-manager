package com.teample.repository;

import com.teample.entity.TodoMemberProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TodoMemberProgressRepository extends JpaRepository<TodoMemberProgress, String> {
    List<TodoMemberProgress> findByProjectIdAndAssignedTrue(String projectId);
    boolean existsByProjectId(String projectId);
    List<TodoMemberProgress> findByProjectIdAndUserIdAndAssignedTrue(String projectId, String userId);
    List<TodoMemberProgress> findByUserIdAndAssignedTrue(String userId);
    List<TodoMemberProgress> findByTodoId(String todoId);
    List<TodoMemberProgress> findByTodoIdAndAssignedTrue(String todoId);
    Optional<TodoMemberProgress> findByTodoIdAndUserId(String todoId, String userId);
    void deleteByTodoId(String todoId);
    void deleteByMinutesId(String minutesId);
    void deleteByProjectId(String projectId);
}