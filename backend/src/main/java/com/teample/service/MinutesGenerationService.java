package com.teample.service;

import com.teample.dto.MinutesRequest;
import com.teample.dto.MinutesResponse;
import com.teample.entity.Project;
import com.teample.repository.ProjectRepository;
import com.teample.repository.ProjectMemberRepository;
import com.teample.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class MinutesGenerationService {
    private final MinutesService minutesService;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ProjectMemberService access;
    private final ClaudeService claude;
    private final AiUsageService usage;

    // Intentionally no transaction around the remote request.
    public MinutesResponse create(String projectId, MinutesRequest request, AuthenticatedUser user, boolean admin, String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{16,100}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 Idempotency-Key가 필요합니다.");
        try { LocalDate.parse(request.getMeetingDate()); }
        catch (DateTimeParseException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "회의 날짜는 YYYY-MM-DD 형식이어야 합니다.");
        }
        access.ensureProjectMember(projectId, user, admin);
        Project project = projects.findById(projectId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (project.blocksNewMinutes(LocalDate.now())) throw new ResponseStatusException(HttpStatus.CONFLICT, "종료되거나 삭제된 프로젝트입니다.");
        claude.ensureConfigured();
        String fingerprint = fingerprint(projectId, request);
        String existing = usage.claim(user.authUserId(), key, fingerprint);
        if (existing != null) return minutesService.findByProjectIdAndId(projectId, existing)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "생성했던 회의록이 삭제되었습니다."));
        try {
            var names = members.findByProjectIdOrderByJoinedAtAsc(projectId).stream().map(m -> m.getDisplayName()).toList();
            var result = claude.analyze(request.getRawText(), project.getName(), names);
            return minutesService.saveGenerated(projectId, request, result, user, admin, key);
        } catch (RuntimeException e) {
            usage.fail(user.authUserId(), key);
            throw e;
        }
    }

    private String fingerprint(String projectId, MinutesRequest request) {
        try {
            // Length delimiters avoid ambiguous concatenations.
            String text = "";
            for (String part : new String[]{projectId, request.getTitle(), request.getMeetingDate(), request.getRawText()}) {
                String value = part == null ? "" : part;
                text += value.length() + ":" + value;
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
