package com.teample.service;

import com.teample.dto.project.JoinProjectInvitationResponse;
import com.teample.dto.project.ProjectInvitationResponse;
import com.teample.entity.Project;
import com.teample.entity.ProjectInvitation;
import com.teample.entity.ProjectMember;
import com.teample.entity.ProjectMemberRole;
import com.teample.repository.ProjectInvitationRepository;
import com.teample.repository.ProjectMemberRepository;
import com.teample.repository.ProjectRepository;
import com.teample.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectInvitationServiceTest {

    @Mock private ProjectInvitationRepository invitationRepository;
    @Mock private ProjectMemberRepository memberRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberService projectMemberService;

    private ProjectInvitationService service;

    @BeforeEach
    void setUp() {
        service = new ProjectInvitationService(
                invitationRepository, memberRepository, projectRepository, projectMemberService);
    }

    @Test
    void createDeactivatesPreviousCodesAndCreatesExpiringCode() {
        when(projectRepository.findById("project-id")).thenReturn(Optional.of(Project.builder().id("project-id").build()));
        AuthenticatedUser owner = new AuthenticatedUser("owner-id", "owner", "owner@example.com");
        when(invitationRepository.findByCode(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(invitationRepository.save(org.mockito.ArgumentMatchers.any(ProjectInvitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectInvitationResponse response = service.create("project-id", owner, false);

        verify(projectMemberService).ensureProjectOwner("project-id", owner, false);
        verify(invitationRepository).deactivateByProjectId("project-id");
        assertThat(response.code()).hasSize(12);
        assertThat(response.expiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void joinAddsJwtSubjectAsMemberAndReturnsProject() {
        Project project = Project.builder().id("project-id").name("Project").build();
        ProjectInvitation invitation = ProjectInvitation.builder()
                .projectId("project-id").code("ABC123").active(true)
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
        AuthenticatedUser user = new AuthenticatedUser("user-id", "member", "member@example.com");
        when(invitationRepository.findByCode("ABC123")).thenReturn(Optional.of(invitation));
        when(projectRepository.findById("project-id")).thenReturn(Optional.of(project));
        when(memberRepository.existsByProjectIdAndUserId("project-id", "user-id")).thenReturn(false);
        ArgumentCaptor<ProjectMember> captor = ArgumentCaptor.forClass(ProjectMember.class);

        JoinProjectInvitationResponse response = service.join(" abc123 ", user);

        verify(memberRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("user-id");
        assertThat(captor.getValue().getRole()).isEqualTo(ProjectMemberRole.MEMBER);
        assertThat(response.projectId()).isEqualTo("project-id");
        assertThat(response.projectName()).isEqualTo("Project");
    }

    @Test
    void joinRejectsExpiredInvitation() {
        ProjectInvitation invitation = ProjectInvitation.builder()
                .projectId("project-id").code("ABC123").active(true)
                .expiresAt(LocalDateTime.now().minusMinutes(1)).build();
        when(invitationRepository.findByCode("ABC123")).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.join("ABC123", new AuthenticatedUser("user-id", null, null)))
                .isInstanceOf(ProjectInvitationService.InvitationExpiredException.class);
    }
}