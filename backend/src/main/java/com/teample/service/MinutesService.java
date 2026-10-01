package com.teample.service;

import com.teample.dto.MinutesEvidence;
import com.teample.dto.MinutesRequest;
import com.teample.dto.MinutesResponse;
import com.teample.dto.MinutesSummary;
import com.teample.dto.TodoItem;
import com.teample.entity.EvidenceData;
import com.teample.entity.Minutes;
import com.teample.entity.Project;
import com.teample.entity.ProjectMember;
import com.teample.entity.TodoData;
import com.teample.repository.MinutesRepository;
import com.teample.repository.ProjectMemberRepository;
import com.teample.repository.ProjectRepository;
import com.teample.repository.TranscriptionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class MinutesService {

    private static final Pattern REQUEST_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,100}$");

    private final MinutesRepository minutesRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ClaudeService claudeService;
    private final ProjectTodoService projectTodoService;
    private final TodoProgressSyncService todoProgressSyncService;
    private final TranscriptionRepository transcriptionRepository;
    private final MinutesStore minutesStore;

    public MinutesService(
            MinutesRepository minutesRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            ClaudeService claudeService,
            ProjectTodoService projectTodoService,
            TodoProgressSyncService todoProgressSyncService,
            TranscriptionRepository transcriptionRepository,
            MinutesStore minutesStore
    ) {
        this.minutesRepository = minutesRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.claudeService = claudeService;
        this.projectTodoService = projectTodoService;
        this.todoProgressSyncService = todoProgressSyncService;
        this.transcriptionRepository = transcriptionRepository;
        this.minutesStore = minutesStore;
    }

    public MinutesResponse create(String projectId, MinutesRequest request) {
        return create(projectId, request, null);
    }

    /**
     * 트랜잭션을 걸지 않는다. Claude 호출(수십 초)이 끝난 뒤 {@link MinutesStore}가 짧은 트랜잭션으로 저장한다.
     * {@code requestKey}(Idempotency-Key)가 같은 요청은 새로 만들지 않고 기존 회의록을 돌려준다.
     */
    public MinutesResponse create(String projectId, MinutesRequest request, String requestKey) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));

        if (project.blocksNewMinutes(AppClock.today())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ended or deleted projects cannot create minutes.");
        }

        String normalizedKey = normalizeRequestKey(requestKey);
        if (normalizedKey != null) {
            Optional<Minutes> existing = minutesRepository.findByProjectIdAndRequestKey(projectId, normalizedKey);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        LocalDate meetingDate = parseMeetingDate(request.getMeetingDate());
        return generate(project, request.getTitle(), meetingDate, request.getRawText(),
                ClaudeService.SourceKind.CHAT, null, normalizedKey);
    }

    /** 음성·영상 전사 텍스트("[mm:ss] 이름: 발언" 줄)로 회의록을 만든다. 호출자도 트랜잭션 밖이어야 한다. */
    public MinutesResponse createFromTranscript(
            Project project, String title, LocalDate meetingDate, String transcriptText, String transcriptionId
    ) {
        if (project.blocksNewMinutes(AppClock.today())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ended or deleted projects cannot create minutes.");
        }
        return generate(project, title, meetingDate, transcriptText, ClaudeService.SourceKind.TRANSCRIPT, transcriptionId, null);
    }

    private MinutesResponse generate(
            Project project, String title, LocalDate meetingDate, String rawText,
            ClaudeService.SourceKind sourceKind, String transcriptionId, String requestKey
    ) {
        ClaudeService.MinutesResult result;
        try {
            result = claudeService.analyze(
                    rawText,
                    project.getName(),
                    resolveProjectMemberNames(project),
                    sourceKind
            );
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }

        List<TodoData> todos = new ArrayList<>(safeList(result.todos()));
        TodoIdentity.assignMissingIds(todos);

        Minutes minutes = Minutes.builder()
                .project(project)
                .meetingDate(meetingDate)
                .rawText(rawText)
                .title(title != null && !title.isBlank() ? title.trim() : result.title())
                .topic(result.topic())
                .discussions(result.discussions())
                .decisions(result.decisions())
                .pending(result.pending())
                .todos(todos)
                .nextAgenda(result.nextAgenda())
                .evidence(result.evidence())
                .transcriptionId(transcriptionId)
                .requestKey(requestKey)
                .build();

        try {
            return toResponse(minutesStore.saveAndSync(project, minutes));
        } catch (DataIntegrityViolationException exception) {
            // 같은 Idempotency-Key로 동시에 들어온 요청이 먼저 저장된 경우: 그 결과를 돌려준다.
            if (requestKey != null) {
                Optional<Minutes> existing = minutesRepository.findByProjectIdAndRequestKey(project.getId(), requestKey);
                if (existing.isPresent()) {
                    return toResponse(existing.get());
                }
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public Optional<MinutesResponse> findById(String id) {
        return minutesRepository.findById(id).map(this::toResponse);
    }

    @Transactional(readOnly = true)
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
        List<String> discussions = new ArrayList<>(safeList(request.getDiscussions()));
        List<String> decisions = new ArrayList<>(safeList(request.getDecisions()));
        List<String> pending = new ArrayList<>(safeList(request.getPending()));
        List<String> nextAgenda = new ArrayList<>(safeList(request.getNextAgenda()));
        List<TodoData> todos = mergeTodos(safeList(minutes.getTodos()), safeList(request.getTodos()));

        // 근거 인용은 위치로 대응되므로, 편집 전 항목 텍스트를 기준으로 다시 정렬한다.
        if (minutes.getEvidence() != null) {
            EvidenceData old = minutes.getEvidence();
            EvidenceData realigned = new EvidenceData();
            realigned.setTitle(old.getTitle());
            realigned.setTopic(old.getTopic());
            realigned.setDiscussions(realignEvidence(safeList(minutes.getDiscussions()), old.getDiscussions(), discussions));
            realigned.setDecisions(realignEvidence(safeList(minutes.getDecisions()), old.getDecisions(), decisions));
            realigned.setPending(realignEvidence(safeList(minutes.getPending()), old.getPending(), pending));
            realigned.setNextAgenda(realignEvidence(safeList(minutes.getNextAgenda()), old.getNextAgenda(), nextAgenda));
            realigned.setTodos(realignTodoEvidence(safeList(minutes.getTodos()), old.getTodos(), todos));
            minutes.setEvidence(realigned);
        }

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            minutes.setTitle(ClaudeService.truncate(request.getTitle(), ClaudeService.MAX_SHORT_TEXT));
        }
        minutes.setTopic(ClaudeService.truncate(request.getTopic(), ClaudeService.MAX_SHORT_TEXT));
        minutes.setDiscussions(discussions);
        minutes.setDecisions(decisions);
        minutes.setPending(pending);
        minutes.setTodos(todos);
        minutes.setNextAgenda(nextAgenda);
        Minutes saved = minutesRepository.save(minutes);
        projectTodoService.synchronizeFromMinutes(saved);
        todoProgressSyncService.syncMinutes(saved.getProject(), saved);
        return toResponse(saved);
    }

    /** 편집 요청의 업무에 기존 ID를 이어 붙인다. ID가 없거나 모르는 ID면 새 업무로 본다. */
    static List<TodoData> mergeTodos(List<TodoData> existing, List<TodoItem> requested) {
        Set<String> knownIds = new HashSet<>();
        for (TodoData todo : existing) {
            if (todo != null && todo.getId() != null) {
                knownIds.add(todo.getId());
            }
        }
        Set<String> used = new HashSet<>();
        List<TodoData> merged = new ArrayList<>();
        for (TodoItem item : requested) {
            if (item == null) {
                continue;
            }
            String id = item.getId() != null && knownIds.contains(item.getId()) && used.add(item.getId())
                    ? item.getId()
                    : TodoIdentity.newId();
            merged.add(new TodoData(
                    id,
                    ClaudeService.truncate(item.getName(), ClaudeService.MAX_SHORT_TEXT),
                    item.getTask(),
                    ClaudeService.truncate(item.getDeadline(), ClaudeService.MAX_SHORT_TEXT)
            ));
        }
        return merged;
    }

    static List<String> realignEvidence(List<String> oldItems, List<String> oldEvidence, List<String> newItems) {
        Map<String, String> evidenceByText = new HashMap<>();
        List<String> safeEvidence = oldEvidence != null ? oldEvidence : Collections.emptyList();
        for (int i = 0; i < oldItems.size() && i < safeEvidence.size(); i++) {
            String key = normalizeKey(oldItems.get(i));
            if (key != null) {
                evidenceByText.putIfAbsent(key, safeEvidence.get(i));
            }
        }
        List<String> realigned = new ArrayList<>();
        for (String item : newItems) {
            String key = normalizeKey(item);
            realigned.add(key != null ? evidenceByText.getOrDefault(key, "") : "");
        }
        return realigned;
    }

    static List<String> realignTodoEvidence(List<TodoData> oldTodos, List<String> oldEvidence, List<TodoData> newTodos) {
        Map<String, String> evidenceById = new HashMap<>();
        List<String> safeEvidence = oldEvidence != null ? oldEvidence : Collections.emptyList();
        for (int i = 0; i < oldTodos.size() && i < safeEvidence.size(); i++) {
            TodoData todo = oldTodos.get(i);
            if (todo != null && todo.getId() != null) {
                evidenceById.putIfAbsent(todo.getId(), safeEvidence.get(i));
            }
        }
        List<String> realigned = new ArrayList<>();
        for (TodoData todo : newTodos) {
            realigned.add(todo != null && todo.getId() != null ? evidenceById.getOrDefault(todo.getId(), "") : "");
        }
        return realigned;
    }

    private static String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
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
        projectTodoService.deleteByMinutes(minutes);
        if (minutes.getTranscriptionId() != null) {
            // 전사와의 연결을 끊어 같은 녹음으로 다시 회의록을 만들 수 있게 한다.
            transcriptionRepository.findById(minutes.getTranscriptionId()).ifPresent(transcription -> {
                if (minutes.getId().equals(transcription.getMinutesId())) {
                    transcription.setMinutesId(null);
                    transcriptionRepository.save(transcription);
                }
            });
        }
        minutesRepository.delete(minutes);
        return true;
    }

    private LocalDate parseMeetingDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "회의 날짜 형식이 올바르지 않습니다.", exception);
        }
    }

    private static String normalizeRequestKey(String requestKey) {
        if (requestKey == null) {
            return null;
        }
        String trimmed = requestKey.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!REQUEST_KEY_PATTERN.matcher(trimmed).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key는 8~100자의 영문·숫자·_·-만 허용합니다.");
        }
        return trimmed;
    }

    private List<String> resolveProjectMemberNames(Project project) {
        List<String> accountMemberNames = projectMemberRepository.findByProjectIdOrderByJoinedAtAsc(project.getId()).stream()
                .map(ProjectMember::getDisplayName)
                .map(this::normalizeOptional)
                .filter(value -> value != null)
                .distinct()
                .toList();
        if (!accountMemberNames.isEmpty()) {
            return accountMemberNames;
        }
        return safeList(project.getMembers()).stream()
                .map(this::normalizeOptional)
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
                        .map(t -> new TodoItem(t.getId(), t.getName(), t.getTask(), t.getDeadline()))
                        .toList())
                .nextAgenda(safeList(minutes.getNextAgenda()))
                .evidence(toEvidenceResponse(minutes.getEvidence()))
                .transcriptionId(minutes.getTranscriptionId())
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
