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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProjectInvitationService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 12;
    private static final int EXPIRY_HOURS = 72;

    private final ProjectInvitationRepository invitationRepository;
    private final ProjectMemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public ProjectInvitationResponse create(String projectId, AuthenticatedUser user, boolean admin) {
        projectMemberService.ensureProjectOwner(projectId, user, admin);
        invitationRepository.deactivateByProjectId(projectId);
        ProjectInvitation invitation = ProjectInvitation.builder()
                .projectId(projectId)
                .code(generateUniqueCode())
                .createdBy(user.authUserId())
                .expiresAt(LocalDateTime.now().plusHours(EXPIRY_HOURS))
                .build();
        return ProjectInvitationResponse.from(invitationRepository.save(invitation));
    }

    @Transactional(readOnly = true)
    public ProjectInvitationResponse findActive(String projectId, AuthenticatedUser user, boolean admin) {
        projectMemberService.ensureProjectOwner(projectId, user, admin);
        return invitationRepository
                .findFirstByProjectIdAndActiveTrueAndExpiresAtAfterOrderByCreatedAtDesc(projectId, LocalDateTime.now())
                .map(ProjectInvitationResponse::from)
                .orElseThrow(() -> new InvitationNotFoundException("Active invitation code not found."));
    }

    @Transactional
    public JoinProjectInvitationResponse join(String code, AuthenticatedUser user) {
        ProjectInvitation invitation = invitationRepository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new InvitationNotFoundException("Invitation code not found."));
        if (!invitation.isActive() || !invitation.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new InvitationExpiredException("Invitation code has expired.");
        }
        Project project = projectRepository.findById(invitation.getProjectId())
                .orElseThrow(() -> new InvitationNotFoundException("Project not found."));
        if (memberRepository.existsByProjectIdAndUserId(project.getId(), user.authUserId())) {
            throw new AlreadyProjectMemberException("User is already a project member.");
        }
        memberRepository.save(ProjectMember.builder()
                .project(project)
                .userId(user.authUserId())
                .displayName(resolveDisplayName(user))
                .role(ProjectMemberRole.MEMBER)
                .build());
        return new JoinProjectInvitationResponse(project.getId(), project.getName(), ProjectMemberRole.MEMBER);
    }

    private String generateUniqueCode() {
        String code;
        do {
            StringBuilder value = new StringBuilder(CODE_LENGTH);
            for (int index = 0; index < CODE_LENGTH; index++) {
                value.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
            }
            code = value.toString();
        } while (invitationRepository.findByCode(code).isPresent());
        return code;
    }

    private String resolveDisplayName(AuthenticatedUser user) {
        if (user.memberKey() != null && !user.memberKey().isBlank()) return user.memberKey().trim();
        if (user.email() != null && !user.email().isBlank()) return user.email().trim();
        return user.authUserId();
    }

    public static class InvitationNotFoundException extends RuntimeException {
        public InvitationNotFoundException(String message) { super(message); }
    }

    public static class InvitationExpiredException extends RuntimeException {
        public InvitationExpiredException(String message) { super(message); }
    }

    public static class AlreadyProjectMemberException extends RuntimeException {
        public AlreadyProjectMemberException(String message) { super(message); }
    }
}