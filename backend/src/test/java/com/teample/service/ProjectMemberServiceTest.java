package com.teample.service;

import com.teample.dto.project.ProjectMemberResponse;
import com.teample.entity.Project;
import com.teample.entity.ProjectMember;
import com.teample.entity.ProjectMemberRole;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectMemberServiceTest {

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProjectRepository projectRepository;

    private ProjectMemberService service;

    @BeforeEach
    void setUp() {
        service = new ProjectMemberService(projectMemberRepository, projectRepository);
    }

    @Test
    void addOwnerStoresSupabaseUserIdAsOwner() {
        Project project = Project.builder().id("project-id").name("project").build();
        AuthenticatedUser user = new AuthenticatedUser("supabase-user-id", "minjae", "minjae@example.com");
        when(projectMemberRepository.existsByProjectIdAndUserId("project-id", "supabase-user-id")).thenReturn(false);
        ArgumentCaptor<ProjectMember> captor = ArgumentCaptor.forClass(ProjectMember.class);

        service.addOwner(project, user);

        verify(projectMemberRepository).save(captor.capture());
        ProjectMember saved = captor.getValue();
        assertThat(saved.getProject()).isEqualTo(project);
        assertThat(saved.getUserId()).isEqualTo("supabase-user-id");
        assertThat(saved.getDisplayName()).isEqualTo("minjae");
        assertThat(saved.getRole()).isEqualTo(ProjectMemberRole.OWNER);
    }

    @Test
    void addOwnerDoesNothingWhenAlreadyMember() {
        Project project = Project.builder().id("project-id").name("project").build();
        AuthenticatedUser user = new AuthenticatedUser("supabase-user-id", "minjae", "minjae@example.com");
        when(projectMemberRepository.existsByProjectIdAndUserId("project-id", "supabase-user-id")).thenReturn(true);

        service.addOwner(project, user);

        verify(projectMemberRepository, never()).save(org.mockito.Mockito.any(ProjectMember.class));
    }

    @Test
    void findProjectMembersReturnsMembersForProjectMember() {
        AuthenticatedUser user = new AuthenticatedUser("supabase-user-id", "minjae", "minjae@example.com");
        LocalDateTime joinedAt = LocalDateTime.of(2026, 8, 21, 10, 0);
        Project project = Project.builder().id("project-id").name("project").build();
        ProjectMember member = ProjectMember.builder()
                .project(project)
                .userId("supabase-user-id")
                .displayName("minjae")
                .role(ProjectMemberRole.OWNER)
                .joinedAt(joinedAt)
                .build();
        when(projectRepository.findById("project-id")).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByProjectIdAndUserId("project-id", "supabase-user-id")).thenReturn(true);
        when(projectMemberRepository.findByProjectIdOrderByJoinedAtAsc("project-id")).thenReturn(List.of(member));

        Optional<List<ProjectMemberResponse>> response = service.findProjectMembers("project-id", user, false);

        assertThat(response).isPresent();
        assertThat(response.get()).hasSize(1);
        assertThat(response.get().get(0).userId()).isEqualTo("supabase-user-id");
        assertThat(response.get().get(0).role()).isEqualTo(ProjectMemberRole.OWNER);
    }

    @Test
    void ensureProjectMemberAllowsLegacyMemberWhenProjectHasNoAccountMembers() {
        AuthenticatedUser user = new AuthenticatedUser("supabase-user-id", "alice", "alice@example.com");
        Project project = Project.builder().id("project-id").members(List.of("alice", "bob")).build();
        when(projectRepository.findById("project-id")).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByProjectIdAndUserId("project-id", "supabase-user-id")).thenReturn(false);
        when(projectMemberRepository.existsByProjectId("project-id")).thenReturn(false);

        service.ensureProjectMember("project-id", user, false);
    }

    @Test
    void ensureProjectOwnerRequiresOwnerRoleWhenAccountMembersExist() {
        AuthenticatedUser user = new AuthenticatedUser("member-user-id", "alice", "alice@example.com");
        Project project = Project.builder().id("project-id").members(List.of("alice")).build();
        when(projectRepository.findById("project-id")).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByProjectIdAndUserIdAndRole(
                "project-id", "member-user-id", ProjectMemberRole.OWNER)).thenReturn(false);
        when(projectMemberRepository.existsByProjectId("project-id")).thenReturn(true);

        assertThatThrownBy(() -> service.ensureProjectOwner("project-id", user, false))
                .isInstanceOf(ProjectMemberService.ProjectMemberAccessDeniedException.class);
    }

    @Test
    void findProjectMembersRejectsNonMember() {
        AuthenticatedUser user = new AuthenticatedUser("other-user-id", "other", "other@example.com");
        Project project = Project.builder().id("project-id").members(List.of("alice")).build();
        when(projectRepository.findById("project-id")).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByProjectIdAndUserId("project-id", "other-user-id")).thenReturn(false);
        when(projectMemberRepository.existsByProjectId("project-id")).thenReturn(true);

        assertThatThrownBy(() -> service.findProjectMembers("project-id", user, false))
                .isInstanceOf(ProjectMemberService.ProjectMemberAccessDeniedException.class);
    }

    @Test
    void findProjectMembersAllowsAdminTestUser() {
        AuthenticatedUser admin = new AuthenticatedUser("admin-test:admin", "admin", null);
        Project project = Project.builder().id("project-id").name("project").build();
        ProjectMember member = ProjectMember.builder()
                .project(project)
                .userId("member-user-id")
                .displayName("member")
                .role(ProjectMemberRole.MEMBER)
                .joinedAt(LocalDateTime.of(2026, 8, 21, 11, 0))
                .build();
        when(projectRepository.findById("project-id")).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdOrderByJoinedAtAsc("project-id")).thenReturn(List.of(member));

        Optional<List<ProjectMemberResponse>> response = service.findProjectMembers("project-id", admin, true);

        assertThat(response).isPresent();
        assertThat(response.get()).extracting(ProjectMemberResponse::userId).containsExactly("member-user-id");
        verify(projectMemberRepository, never()).existsByProjectIdAndUserId("project-id", "admin-test:admin");
    }

    @Test
    void findProjectMembersReturnsEmptyWhenProjectMissing() {
        AuthenticatedUser user = new AuthenticatedUser("supabase-user-id", "minjae", "minjae@example.com");
        when(projectRepository.findById("missing-id")).thenReturn(Optional.empty());

        Optional<List<ProjectMemberResponse>> response = service.findProjectMembers("missing-id", user, false);

        assertThat(response).isEmpty();
    }
}
