package com.teample.service;

import com.teample.dto.MinutesRequest;
import com.teample.dto.MinutesResponse;
import com.teample.dto.MinutesSummary;
import com.teample.dto.TodoItem;
import com.teample.entity.Minutes;
import com.teample.entity.Project;
import com.teample.entity.TodoData;
import com.teample.repository.MinutesRepository;
import com.teample.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MinutesService {

    private final MinutesRepository minutesRepository;
    private final ProjectRepository projectRepository;
    private final ClaudeService claudeService;

    public MinutesResponse create(String projectId, MinutesRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("프로젝트를 찾을 수 없습니다."));

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
                .build();

        Minutes saved = minutesRepository.save(minutes);
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

    public Optional<MinutesResponse> update(String id, MinutesResponse request) {
        return minutesRepository.findById(id).map(minutes -> {
            minutes.setTitle(request.getTitle());
            minutes.setTopic(request.getTopic());
            minutes.setDiscussions(new ArrayList<>(request.getDiscussions()));
            minutes.setDecisions(new ArrayList<>(request.getDecisions()));
            minutes.setPending(new ArrayList<>(request.getPending()));
            minutes.setTodos(request.getTodos().stream()
                    .map(t -> new TodoData(t.getName(), t.getTask(), t.getDeadline()))
                    .toList());
            minutes.setNextAgenda(new ArrayList<>(request.getNextAgenda()));
            Minutes saved = minutesRepository.save(minutes);
            return toResponse(saved);
        });
    }

    public boolean delete(String id) {
        return minutesRepository.findById(id).map(minutes -> {
            minutesRepository.delete(minutes);
            return true;
        }).orElse(false);
    }

    private MinutesResponse toResponse(Minutes m) {
        return MinutesResponse.builder()
                .id(m.getId())
                .title(m.getTitle())
                .topic(m.getTopic())
                .discussions(m.getDiscussions())
                .decisions(m.getDecisions())
                .pending(m.getPending())
                .todos(m.getTodos().stream()
                        .map(t -> new TodoItem(t.getName(), t.getTask(), t.getDeadline()))
                        .toList())
                .nextAgenda(m.getNextAgenda())
                .build();
    }
}
