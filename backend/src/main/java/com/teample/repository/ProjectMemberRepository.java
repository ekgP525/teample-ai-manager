package com.teample.repository;

import com.teample.entity.ProjectMember;
import com.teample.entity.ProjectMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, String> {

    List<ProjectMember> findByProjectIdOrderByJoinedAtAsc(String projectId);

    List<ProjectMember> findByUserIdOrderByJoinedAtAsc(String userId);

    boolean existsByProjectId(String projectId);

    boolean existsByProjectIdAndUserId(String projectId, String userId);

    boolean existsByProjectIdAndUserIdAndRole(String projectId, String userId, ProjectMemberRole role);

    boolean existsByProjectIdAndRole(String projectId, ProjectMemberRole role);

    void deleteByProjectId(String projectId);
}
