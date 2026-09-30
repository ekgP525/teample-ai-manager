package com.teample.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teample.dto.MinutesResponse;
import com.teample.dto.transcription.TranscriptionResponse;
import com.teample.entity.Project;
import com.teample.entity.TranscriptSegment;
import com.teample.entity.Transcription;
import com.teample.entity.TranscriptionStatus;
import com.teample.repository.ProjectRepository;
import com.teample.repository.TranscriptionRepository;
import com.teample.security.AuthenticatedUser;
import com.teample.service.transcription.MediaStorageService;
import com.teample.service.transcription.SpeechToTextProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 음성·영상 전사의 생명주기: 업로드 → 비동기 STT → 화자 매핑 → 회의록 생성.
 */
@Service
public class TranscriptionService {

    static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "m4a", "wav", "flac", "amr", "aac", "ogg", "oga", "opus", "weba");
    static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov", "mkv", "avi", "webm", "m4v");

    private static final TypeReference<List<TranscriptSegment>> SEGMENTS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, String>> SPEAKER_NAMES_TYPE = new TypeReference<>() {
    };

    private final TranscriptionRepository transcriptionRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;
    private final PlanService planService;
    private final MediaStorageService mediaStorageService;
    private final SpeechToTextProvider speechToTextProvider;
    private final TranscriptionProcessor transcriptionProcessor;
    private final MinutesService minutesService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final long maxFileSizeBytes;

    public TranscriptionService(
            TranscriptionRepository transcriptionRepository,
            ProjectRepository projectRepository,
            ProjectMemberService projectMemberService,
            PlanService planService,
            MediaStorageService mediaStorageService,
            SpeechToTextProvider speechToTextProvider,
            TranscriptionProcessor transcriptionProcessor,
            MinutesService minutesService,
            @Value("${app.media.max-file-size-mb:500}") long maxFileSizeMb
    ) {
        this.transcriptionRepository = transcriptionRepository;
        this.projectRepository = projectRepository;
        this.projectMemberService = projectMemberService;
        this.planService = planService;
        this.mediaStorageService = mediaStorageService;
        this.speechToTextProvider = speechToTextProvider;
        this.transcriptionProcessor = transcriptionProcessor;
        this.minutesService = minutesService;
        this.maxFileSizeBytes = maxFileSizeMb * 1024L * 1024L;
    }

    @Transactional
    public TranscriptionResponse create(
            String projectId,
            AuthenticatedUser user,
            boolean admin,
            MultipartFile file,
            Integer expectedSpeakers,
            String language,
            boolean consentConfirmed
    ) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectMemberService.ProjectNotFoundException("Project not found."));
        projectMemberService.ensureProjectMember(project, user, admin);
        if (project.blocksNewMinutes(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "종료되었거나 삭제된 프로젝트에는 회의록을 추가할 수 없습니다.");
        }
        if (!consentConfirmed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "회의 참여자의 녹음 동의 확인이 필요합니다.");
        }
        if (!speechToTextProvider.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "음성 인식 서비스가 아직 설정되지 않았습니다. 관리자에게 문의해 주세요.");
        }
        planService.ensureTranscriptionAllowed(user, admin);

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업로드할 파일이 비어 있습니다.");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "파일이 너무 큽니다. 최대 " + (maxFileSizeBytes / 1024 / 1024) + "MB까지 업로드할 수 있습니다.");
        }
        String originalName = file.getOriginalFilename() == null ? "recording" : file.getOriginalFilename();
        String extension = extensionOf(originalName, file.getContentType());
        String mediaKind = mediaKindOf(extension);
        if (mediaKind == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "지원하지 않는 파일 형식입니다. mp3, m4a, wav, mp4, mov, webm 등을 올려 주세요.");
        }
        if (expectedSpeakers != null && (expectedSpeakers < 0 || expectedSpeakers > 20)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "예상 화자 수는 0~20 사이여야 합니다.");
        }

        String storagePath;
        try (InputStream input = file.getInputStream()) {
            storagePath = mediaStorageService.store(input, extension);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일을 저장하지 못했습니다.", e);
        }

        Transcription transcription = Transcription.builder()
                .project(project)
                .createdBy(user.authUserId())
                .status(TranscriptionStatus.QUEUED)
                .sourceFileName(originalName.length() > 500 ? originalName.substring(0, 500) : originalName)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .mediaKind(mediaKind)
                .storagePath(storagePath)
                .provider(speechToTextProvider.name())
                .language(language == null || language.isBlank() ? "ko" : language.trim())
                .expectedSpeakers(expectedSpeakers)
                .build();
        Transcription saved = transcriptionRepository.save(transcription);
        dispatchAfterCommit(saved.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<TranscriptionResponse> findByProject(String projectId, AuthenticatedUser user, boolean admin) {
        projectMemberService.ensureProjectMember(projectId, user, admin);
        return transcriptionRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<TranscriptionResponse> find(String projectId, String id, AuthenticatedUser user, boolean admin) {
        projectMemberService.ensureProjectMember(projectId, user, admin);
        return transcriptionRepository.findById(id)
                .filter(transcription -> transcription.belongsToProject(projectId))
                .map(this::toResponse);
    }

    @Transactional
    public Optional<TranscriptionResponse> updateSpeakerNames(
            String projectId, String id, Map<String, String> speakerNames, AuthenticatedUser user, boolean admin
    ) {
        projectMemberService.ensureProjectMember(projectId, user, admin);
        return transcriptionRepository.findById(id)
                .filter(transcription -> transcription.belongsToProject(projectId))
                .map(transcription -> {
                    if (transcription.getStatus() != TranscriptionStatus.COMPLETED) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "전사가 완료된 뒤에 화자 이름을 지정할 수 있습니다.");
                    }
                    Map<String, String> cleaned = new LinkedHashMap<>();
                    if (speakerNames != null) {
                        speakerNames.forEach((label, name) -> {
                            if (label != null && !label.isBlank() && name != null && !name.isBlank()) {
                                cleaned.put(label.trim(), name.trim());
                            }
                        });
                    }
                    transcription.setSpeakerNamesJson(writeJson(cleaned));
                    return toResponse(transcriptionRepository.save(transcription));
                });
    }

    /** 전사 결과를 기존 회의록 파이프라인에 넣어 회의록을 만든다. 한 전사당 한 번만 가능하다. */
    @Transactional
    public Optional<MinutesResponse> createMinutes(
            String projectId, String id, String title, String meetingDate, AuthenticatedUser user, boolean admin
    ) {
        projectMemberService.ensureProjectMember(projectId, user, admin);
        return transcriptionRepository.findById(id)
                .filter(transcription -> transcription.belongsToProject(projectId))
                .map(transcription -> {
                    if (transcription.getStatus() != TranscriptionStatus.COMPLETED) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "전사가 완료된 뒤에 회의록을 만들 수 있습니다.");
                    }
                    if (transcription.hasMinutes()) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "이 전사로 만든 회의록이 이미 있습니다.");
                    }
                    List<TranscriptSegment> segments = readSegments(transcription);
                    if (segments.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "전사 결과에 인식된 발언이 없습니다.");
                    }
                    LocalDate date = parseMeetingDate(meetingDate);
                    String rawText = buildTranscriptText(segments, readSpeakerNames(transcription));
                    MinutesResponse minutes = minutesService.createFromTranscript(
                            transcription.getProject(), title, date, rawText, transcription.getId());
                    transcription.setMinutesId(minutes.getId());
                    transcriptionRepository.save(transcription);
                    return minutes;
                });
    }

    @Transactional(readOnly = true)
    public Optional<AudioFile> openAudio(String projectId, String id, AuthenticatedUser user, boolean admin) {
        projectMemberService.ensureProjectMember(projectId, user, admin);
        return transcriptionRepository.findById(id)
                .filter(transcription -> transcription.belongsToProject(projectId))
                .filter(transcription -> mediaStorageService.exists(transcription.getStoragePath()))
                .map(transcription -> new AudioFile(
                        new FileSystemResource(mediaStorageService.resolve(transcription.getStoragePath())),
                        transcription.getContentType() == null ? "application/octet-stream" : transcription.getContentType(),
                        transcription.getSourceFileName()
                ));
    }

    @Transactional
    public boolean delete(String projectId, String id, AuthenticatedUser user, boolean admin) {
        projectMemberService.ensureProjectMember(projectId, user, admin);
        return transcriptionRepository.findById(id)
                .filter(transcription -> transcription.belongsToProject(projectId))
                .map(transcription -> {
                    if (!transcription.getStatus().isTerminal()) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "처리 중인 전사는 삭제할 수 없습니다.");
                    }
                    mediaStorageService.delete(transcription.getStoragePath());
                    transcriptionRepository.delete(transcription);
                    return true;
                })
                .orElse(false);
    }

    /** 전사 텍스트를 "[mm:ss] 이름: 발언" 줄로 만든다. Claude 프롬프트와 evidence 매칭이 이 형식에 의존한다. */
    String buildTranscriptText(List<TranscriptSegment> segments, Map<String, String> speakerNames) {
        StringBuilder builder = new StringBuilder();
        for (TranscriptSegment segment : segments) {
            String name = speakerNames.getOrDefault(segment.getSpeaker(), "화자 " + segment.getSpeaker());
            builder.append('[').append(formatTimestamp(segment.getStartMs())).append("] ")
                    .append(name).append(": ").append(segment.getText().trim()).append('\n');
        }
        return builder.toString().trim();
    }

    static String formatTimestamp(long ms) {
        long totalSeconds = Math.max(ms, 0) / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);
    }

    List<TranscriptSegment> readSegments(Transcription transcription) {
        String json = transcription.getSegmentsJson();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, SEGMENTS_TYPE);
        } catch (IOException e) {
            return List.of();
        }
    }

    Map<String, String> readSpeakerNames(Transcription transcription) {
        String json = transcription.getSpeakerNamesJson();
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, SPEAKER_NAMES_TYPE);
        } catch (IOException e) {
            return Map.of();
        }
    }

    String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("JSON 직렬화 실패", e);
        }
    }

    TranscriptionResponse toResponse(Transcription transcription) {
        List<TranscriptSegment> segments = readSegments(transcription);
        Set<String> labels = new LinkedHashSet<>();
        for (TranscriptSegment segment : segments) {
            labels.add(segment.getSpeaker());
        }
        return new TranscriptionResponse(
                transcription.getId(),
                transcription.getProject() != null ? transcription.getProject().getId() : null,
                transcription.getStatus(),
                transcription.getSourceFileName(),
                transcription.getMediaKind(),
                transcription.getDurationMs(),
                transcription.getExpectedSpeakers(),
                new ArrayList<>(labels),
                readSpeakerNames(transcription),
                segments,
                transcription.getErrorMessage(),
                transcription.getMinutesId(),
                mediaStorageService.exists(transcription.getStoragePath()),
                transcription.getCreatedAt(),
                transcription.getCompletedAt()
        );
    }

    static String extensionOf(String fileName, String contentType) {
        String name = fileName == null ? "" : fileName.trim();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            return name.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        }
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (type.contains("webm")) return "webm";
        if (type.contains("mp4") || type.contains("m4a") || type.contains("aac")) return "m4a";
        if (type.contains("mpeg") || type.contains("mp3")) return "mp3";
        if (type.contains("wav")) return "wav";
        if (type.contains("ogg")) return "ogg";
        return "";
    }

    static String mediaKindOf(String extension) {
        if (extension == null || extension.isBlank()) {
            return null;
        }
        if (AUDIO_EXTENSIONS.contains(extension)) {
            return "AUDIO";
        }
        if (VIDEO_EXTENSIONS.contains(extension)) {
            return "VIDEO";
        }
        return null;
    }

    private LocalDate parseMeetingDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "회의 날짜 형식이 올바르지 않습니다.");
        }
    }

    private void dispatchAfterCommit(String transcriptionId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    transcriptionProcessor.process(transcriptionId);
                }
            });
        } else {
            transcriptionProcessor.process(transcriptionId);
        }
    }

    public record AudioFile(Resource resource, String contentType, String fileName) {
    }
}
