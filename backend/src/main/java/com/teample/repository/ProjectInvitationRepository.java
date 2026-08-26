package com.teample.repository;

import com.teample.entity.ProjectInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitation, String> {

    Optional<ProjectInvitation> findByCode(String code);

    @Modifying
    @Query("update ProjectInvitation invitation set invitation.active = false "
            + "where invitation.projectId = :projectId and invitation.active = true")
    int deactivateByProjectId(String projectId);

    @Modifying
    @Query("delete from ProjectInvitation invitation where invitation.projectId = :projectId")
    int deleteByProjectId(String projectId);
}