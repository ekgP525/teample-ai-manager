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
public class MinutesService {

    private final MinutesRepository minutesRepository;
    private final ProjectRepository projectRepository;
    private final ClaudeService claudeService;
    private final ProjectTodoService projectTodoService;
    private final TodoProgressSyncService todoProgressSyncService;

    @Transactional
    public MinutesResponse create(String projectId, MinutesRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found."));

        if (project.blocksNewMinutes(LocalDate.now())) {
            throw new IllegalStateException("Ended or deleted projects cannot create minutes.");
        }

        ClaudeService.MinutesResult result = claudeService.analyze(
                request.getRawText(),
                project.getName(),
                project.getMembers()
        );

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

        Minutes saved = minutesRepository.save(minutes);
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
        return minutesRepository.findById(id)
                .filter(minutes -> belongsToProject(minutes, projectId))
                .map(minutes -> updateMinutes(minutes, request));
    }

    @Transactional
    public Optional<MinutesResponse> update(String id, MinutesResponse request) {
        return minutesRepository.findById(id).map(minutes -> updateMinutes(minutes, request));
    }

    private MinutesResponse updateMinutes(Minutes minutes, MinutesResponse request) {
            minutes.setTitle(request.getTitle());
            minutes.setTopic(request.getTopic());
            minutes.setDiscussions(new ArrayList<>(safeList(request.getDiscussions())));
            minutes.setDecisions(new ArrayList<>(safeList(request.getDecisions())));
            minutes.setPending(new ArrayList<>(safeList(request.getPending())));
            minutes.setTodos(safeList(request.getTodos()).stream()
                    .map(t -> new TodoData(t.getName(), t.getTask(), t.getDeadline()))
                    .toList());
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

    private MinutesResponse toResponse(Minutes minutes) {
        return MinutesResponse.builder()
                .id(minutes.getId())
                .title(minutes.getTitle())
                .topic(minutes.getTopic())
                .discussions(safeList(minutes.getDiscussions()))
                .decisions(safeList(minutes.getDecisions()))
                .pending(safeList(minutes.getPending()))
                .todos(safeList(minutes.getTodos()).stream()
                        .map(t -> new TodoItem(t.getName(), t.getTask(), t.getDeadline()))
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
}
