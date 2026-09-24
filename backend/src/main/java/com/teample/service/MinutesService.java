package com.teample.service;

import com.teample.dto.MinutesEvidence;
import com.teample.dto.MinutesRequest;
import com.teample.dto.MinutesResponse;
import com.teample.dto.MinutesSummary;
import com.teample.dto.TodoItem;
import com.teample.entity.EvidenceData;
import com.teample.entity.Minutes;
import com.teample.entity.Project;
import com.teample.entity.ProjectStatus;
import com.teample.entity.TodoData;
import com.teample.repository.MinutesRepository;
import com.teample.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MinutesService {

    private final MinutesRepository minutesRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;
    private final AiUsageService aiUsageService;
    private final ProjectTodoService projectTodoService;
    private final TodoProgressSyncService todoProgressSyncService;

    @Transactional
    public MinutesResponse saveGenerated(String projectId, MinutesRequest request, ClaudeService.MinutesResult result, com.teample.security.AuthenticatedUser user, boolean admin, String key) {
        Project project = projectRepository.findLockedById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found."));
        projectMemberService.ensureProjectMember(project, user, admin);

        if (project.blocksNewMinutes(LocalDate.now())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "종료되거나 삭제된 프로젝트입니다.");
        }

        Minutes minutes = Minutes.builder()
                .project(project)
                .meetingDate(LocalDate.parse(request.getMeetingDate()))
                .rawText(request.getRawText())
                .title(request.getTitle() != null && !request.getTitle().isBlank()
                        ? request.getTitle() : result.title())
                .topic(result.topic())
                .discussions(result.discussions())
                .decisions(result.decisions())
                .pending(result.pending())
                .todos(result.todos())
                .nextAgenda(result.nextAgenda())
                .evidence(result.evidence())
                .build();

        initializeSourceIndexes(minutes);
        Minutes saved = minutesRepository.save(minutes);
        aiUsageService.complete(user.authUserId(), key, saved.getId());
        projectTodoService.synchronizeFromMinutes(saved);
        todoProgressSyncService.syncMinutes(project, saved);
        return toResponse(saved);
    }

    public List<MinutesSummary> findByProjectId(String projectId) {
        return minutesRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(m -> MinutesSummary.builder()
                        .id(m.getId())
                        .title(m.getTitle())
                        .subject(m.getProject().getName())
                        .meetingDate(m.getMeetingDate().toString())
                        .topic(m.getTopic())
                        .createdAt(m.getCreatedAt() != null ? m.getCreatedAt().toString() : "")
                        .build())
                .toList();
    }

    public Optional<MinutesResponse> findById(String id) {
        return minutesRepository.findById(id).map(this::toResponse);
    }

    public Optional<MinutesResponse> findByProjectIdAndId(String projectId, String id) {
        return minutesRepository.findById(id)
                .filter(minutes -> belongsToProject(minutes, projectId))
                .map(this::toResponse);
    }

    @Transactional
    public Optional<MinutesResponse> update(String projectId, String id, MinutesResponse request) {
        return minutesRepository.findLockedById(id)
                .filter(minutes -> belongsToProject(minutes, projectId))
                .map(minutes -> updateMinutes(minutes, request));
    }

    @Transactional
    public Optional<MinutesResponse> update(String id, MinutesResponse request) {
        return minutesRepository.findLockedById(id).map(minutes -> updateMinutes(minutes, request));
    }

    private MinutesResponse updateMinutes(Minutes minutes, MinutesResponse request) {
            initializeSourceIndexes(minutes);
            var existingIds = safeList(minutes.getTodos()).stream().map(TodoData::getSourceIndex).collect(java.util.stream.Collectors.toSet());
            int nextId = minutes.getNextTodoIndex();
            var seen = new java.util.HashSet<Integer>();
            var updatedTodos = new ArrayList<TodoData>();
            for (TodoItem item : safeList(request.getTodos())) {
                Integer sourceId = item.getSourceIndex();
                if (sourceId != null && (!existingIds.contains(sourceId) || !seen.add(sourceId))) {
                    throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "할 일 목록이 변경되었습니다. 새로고침 후 다시 시도해 주세요.");
                }
                if (sourceId == null) sourceId = nextId++;
                updatedTodos.add(new TodoData(sourceId, item.getName(), item.getTask(), item.getDeadline()));
            }
            EvidenceData evidence = minutes.getEvidence();
            if (evidence != null) {
                minutes.setEvidence(new EvidenceData(
                        java.util.Objects.equals(minutes.getTitle(), request.getTitle()) ? evidence.getTitle() : "",
                        java.util.Objects.equals(minutes.getTopic(), request.getTopic()) ? evidence.getTopic() : "",
                        alignEvidence(minutes.getDiscussions(), request.getDiscussions(), evidence.getDiscussions()),
                        alignEvidence(minutes.getDecisions(), request.getDecisions(), evidence.getDecisions()),
                        alignEvidence(minutes.getPending(), request.getPending(), evidence.getPending()),
                        alignEvidence(minutes.getTodos(), updatedTodos, evidence.getTodos()),
                        alignEvidence(minutes.getNextAgenda(), request.getNextAgenda(), evidence.getNextAgenda())));
            }
            minutes.setTitle(request.getTitle());
            minutes.setTopic(request.getTopic());
            minutes.setDiscussions(new ArrayList<>(safeList(request.getDiscussions())));
            minutes.setDecisions(new ArrayList<>(safeList(request.getDecisions())));
            minutes.setPending(new ArrayList<>(safeList(request.getPending())));
            minutes.setNextTodoIndex(nextId);
            minutes.setTodos(updatedTodos);
            minutes.setNextAgenda(new ArrayList<>(safeList(request.getNextAgenda())));
            Minutes saved = minutesRepository.save(minutes);
            projectTodoService.synchronizeFromMinutes(saved);
            todoProgressSyncService.syncMinutes(saved.getProject(), saved);
            return toResponse(saved);
    }

    @Transactional
    public boolean delete(String projectId, String id) {
        return minutesRepository.findById(id)
                .filter(minutes -> belongsToProject(minutes, projectId))
                .map(this::deleteMinutes)
                .orElse(false);
    }

    @Transactional
    public boolean delete(String id) {
        return minutesRepository.findById(id)
                .map(this::deleteMinutes)
                .orElse(false);
    }

    private boolean deleteMinutes(Minutes minutes) {
        todoProgressSyncService.deleteByMinutes(minutes);
        minutesRepository.delete(minutes);
        return true;
    }

    private boolean belongsToProject(Minutes minutes, String projectId) {
        return minutes != null
                && minutes.getProject() != null
                && minutes.getProject().getId() != null
                && minutes.getProject().getId().equals(projectId);
    }

    private void initializeSourceIndexes(Minutes minutes) {
        List<TodoData> todos = safeList(minutes.getTodos());
        for (int index = 0; index < todos.size(); index++) {
            if (todos.get(index).getSourceIndex() == null) todos.get(index).setSourceIndex(index);
            minutes.setNextTodoIndex(Math.max(minutes.getNextTodoIndex(), todos.get(index).getSourceIndex() + 1));
        }
    }

    private MinutesResponse toResponse(Minutes minutes) {
        initializeSourceIndexes(minutes);
        return MinutesResponse.builder()
                .id(minutes.getId())
                .title(minutes.getTitle())
                .topic(minutes.getTopic())
                .discussions(safeList(minutes.getDiscussions()))
                .decisions(safeList(minutes.getDecisions()))
                .pending(safeList(minutes.getPending()))
                .todos(safeList(minutes.getTodos()).stream()
                        .map(t -> new TodoItem(t.getSourceIndex(), t.getName(), t.getTask(), t.getDeadline()))
                        .toList())
                .nextAgenda(safeList(minutes.getNextAgenda()))
                .evidence(toEvidenceResponse(minutes.getEvidence()))
                .build();
    }

    private MinutesEvidence toEvidenceResponse(EvidenceData evidence) {
        if (evidence == null) {
            return null;
        }
        return MinutesEvidence.builder()
                .title(evidence.getTitle())
                .topic(evidence.getTopic())
                .discussions(safeList(evidence.getDiscussions()))
                .decisions(safeList(evidence.getDecisions()))
                .pending(safeList(evidence.getPending()))
                .todos(safeList(evidence.getTodos()))
                .nextAgenda(safeList(evidence.getNextAgenda()))
                .build();
    }

    private <T> List<T> safeList(List<T> value) {
        return value != null ? value : Collections.emptyList();
    }

    private <T> List<String> alignEvidence(List<T> before, List<T> after, List<String> quotes) {
        return safeList(after).stream().map(item -> {
            int index = safeList(before).indexOf(item);
            return index >= 0 && index < safeList(quotes).size() ? quotes.get(index) : "";
        }).toList();
    }
}
