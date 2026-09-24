package com.teample.repository;

import com.teample.entity.ProjectMember;
import com.teample.entity.ProjectMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, String> {

    List<ProjectMember> findByProjectIdOrderByJoinedAtAsc(String projectId);

    List<ProjectMember> findByUserIdOrderByJoinedAtAsc(String userId);

    boolean existsByProjectId(String projectId);

    boolean existsByProjectIdAndUserId(String projectId, String userId);

    boolean existsByProjectIdAndUserIdAndRole(String projectId, String userId, ProjectMemberRole role);

    boolean existsByProjectIdAndRole(String projectId, ProjectMemberRole role);

    Optional<ProjectMember> findByProjectIdAndUserId(String projectId, String userId);

    void deleteByProjectIdAndUserId(String projectId, String userId);

    void deleteByProjectId(String projectId);
}
