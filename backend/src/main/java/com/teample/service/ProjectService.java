package com.teample.service;

import com.teample.dto.ProjectRequest;
import com.teample.dto.ProjectResponse;
import com.teample.entity.Project;
import com.teample.entity.ProjectMember;
import com.teample.entity.ProjectStatus;
import com.teample.repository.IntegratedTodoRepository;
import com.teample.repository.MinutesRepository;
import com.teample.repository.ProjectInvitationRepository;
import com.teample.repository.ProjectMemberRepository;
import com.teample.repository.ProjectRepository;
import com.teample.repository.TranscriptionRepository;
import com.teample.service.transcription.MediaStorageService;
import com.teample.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final IntegratedTodoRepository integratedTodoRepository;
    private final MinutesRepository minutesRepository;
    private final TodoProgressSyncService todoProgressSyncService;
    private final ProjectMemberService projectMemberService;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectInvitationRepository projectInvitationRepository;
    private final TranscriptionRepository transcriptionRepository;
    private final MediaStorageService mediaStorageService;

    public ProjectResponse create(ProjectRequest request) {
        return create(request, null);
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request, AuthenticatedUser owner) {
        Project project = Project.builder()
                .name(request.getName())
                .members(List.of())
                .endDate(request.getEndDate())
                .build();
        synchronizeStatus(project);
        Project saved = projectRepository.save(project);
        projectMemberService.addOwner(saved, owner);
        return ProjectResponse.from(saved, resolveProjectMemberNames(saved, owner));
    }

    @Transactional
    public List<ProjectResponse> findAll() {
        return projectRepository.findAll().stream()
                .peek(this::synchronizeStatus)
                .filter(Project::isVisibleInActiveList)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<ProjectResponse> findAll(AuthenticatedUser user, boolean admin) {
        List<Project> candidates;
        if (admin) {
            candidates = projectRepository.findAll();
        } else if (user == null) {
            return List.of();
        } else {
            // 전체 테이블을 훑지 않고 내 멤버십 행에서 프로젝트를 가져온다.
            candidates = projectMemberRepository.findByUserIdOrderByJoinedAtAsc(user.authUserId()).stream()
                    .map(ProjectMember::getProject)
                    .filter(project -> project != null && project.getId() != null)
                    .toList();
        }
        return candidates.stream()
                .peek(this::synchronizeStatus)
                .filter(Project::isVisibleInActiveList)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<ProjectResponse> findTrash() {
        return projectRepository.findByStatus(ProjectStatus.DELETED).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<ProjectResponse> findTrash(AuthenticatedUser user, boolean admin) {
        return projectRepository.findByStatus(ProjectStatus.DELETED).stream()
                .filter(project -> projectMemberService.canAccessProject(project, user, admin))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public int synchronizeExpiredProjects() {
        LocalDate today = AppClock.today();
        return (int) projectRepository.findAll().stream()
                .filter(Project::isVisibleInActiveList)
                .filter(project -> project.hasEndDatePassed(today))
                .peek(this::synchronizeStatus)
                .filter(project -> project.getResolvedStatus().isEnded())
                .count();
    }

    @Transactional
    public Optional<ProjectResponse> findById(String id) {
        return projectRepository.findById(id).map(project -> {
            synchronizeStatus(project);
            return toResponse(project);
        });
    }

    @Transactional
    public boolean delete(String id) {
        return projectRepository.findById(id).map(project -> {
            project.markDeleted(AppClock.now());
            return true;
        }).orElse(false);
    }

    @Transactional
    public Optional<ProjectResponse> restore(String id) {
        return projectRepository.findById(id)
                .filter(Project::isDeleted)
                .map(project -> {
                    project.restore(AppClock.today(), AppClock.now());
                    return toResponse(project);
                });
    }

    /** 휴지통(DELETED)에 있는 프로젝트만 영구 삭제한다. 파일은 트랜잭션이 커밋된 뒤에 지운다. */
    @Transactional
    public boolean permanentlyDelete(String id) {
        return projectRepository.findById(id)
                .filter(Project::isDeleted)
                .map(project -> {
                    List<String> mediaPaths = transcriptionRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream()
                            .map(transcription -> transcription.getStoragePath())
                            .filter(path -> path != null && !path.isBlank())
                            .toList();
                    projectInvitationRepository.deleteByProjectId(project.getId());
                    projectMemberRepository.deleteByProjectId(project.getId());
                    todoProgressSyncService.deleteByProject(project);
                    integratedTodoRepository.deleteByProjectId(project.getId());
                    transcriptionRepository.deleteByProjectId(project.getId());
                    minutesRepository.deleteByProjectId(project.getId());
                    projectRepository.delete(project);
                    deleteMediaAfterCommit(mediaPaths);
                    return true;
                })
                .orElse(false);
    }

    private void deleteMediaAfterCommit(List<String> mediaPaths) {
        if (mediaPaths.isEmpty()) {
            return;
        }
        Runnable cleanup = () -> mediaPaths.forEach(mediaStorageService::delete);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
        } else {
            cleanup.run();
        }
    }

    private ProjectResponse toResponse(Project project) {
        return ProjectResponse.from(project, resolveProjectMemberNames(project, null));
    }

    private List<String> resolveProjectMemberNames(Project project, AuthenticatedUser fallbackOwner) {
        List<String> accountMemberNames = projectMemberRepository.findByProjectIdOrderByJoinedAtAsc(project.getId()).stream()
                .map(ProjectMember::getDisplayName)
                .map(this::normalizeOptional)
                .filter(value -> value != null)
                .distinct()
                .toList();
        if (!accountMemberNames.isEmpty()) {
            return accountMemberNames;
        }
        if (fallbackOwner != null) {
            String ownerName = normalizeOptional(fallbackOwner.memberKey());
            if (ownerName != null) {
                return List.of(ownerName);
            }
        }
        return project.getMembers() != null ? project.getMembers() : List.of();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void synchronizeStatus(Project project) {
        project.synchronizeLifecycle(AppClock.today(), AppClock.now());
    }
}
