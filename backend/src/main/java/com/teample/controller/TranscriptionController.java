package com.teample.controller;

import com.teample.dto.MinutesResponse;
import com.teample.dto.transcription.SpeakerNamesRequest;
import com.teample.dto.transcription.TranscriptionMinutesRequest;
import com.teample.dto.transcription.TranscriptionResponse;
import com.teample.security.AuthenticatedUser;
import com.teample.security.SupabaseAuthenticationFilter;
import com.teample.service.ProjectMemberService;
import com.teample.service.TranscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/transcriptions")
@RequiredArgsConstructor
public class TranscriptionController {

    private final TranscriptionService transcriptionService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TranscriptionResponse> upload(
            @PathVariable String projectId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "expectedSpeakers", required = false) Integer expectedSpeakers,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "consent", required = false, defaultValue = "false") boolean consent,
            HttpServletRequest request
    ) {
        TranscriptionResponse response = transcriptionService.create(
                projectId, authenticatedUser(request), isAdmin(request), file, expectedSpeakers, language, consent);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<TranscriptionResponse>> list(@PathVariable String projectId, HttpServletRequest request) {
        return ResponseEntity.ok(transcriptionService.findByProject(projectId, authenticatedUser(request), isAdmin(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TranscriptionResponse> get(
            @PathVariable String projectId, @PathVariable String id, HttpServletRequest request
    ) {
        return transcriptionService.find(projectId, id, authenticatedUser(request), isAdmin(request))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/speakers")
    public ResponseEntity<TranscriptionResponse> updateSpeakers(
            @PathVariable String projectId,
            @PathVariable String id,
            @Valid @RequestBody SpeakerNamesRequest body,
            HttpServletRequest request
    ) {
        return transcriptionService.updateSpeakerNames(projectId, id, body.speakerNames(), authenticatedUser(request), isAdmin(request))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/minutes")
    public ResponseEntity<MinutesResponse> createMinutes(
            @PathVariable String projectId,
            @PathVariable String id,
            @Valid @RequestBody TranscriptionMinutesRequest body,
            HttpServletRequest request
    ) {
        return transcriptionService.createMinutes(projectId, id, body.title(), body.meetingDate(), authenticatedUser(request), isAdmin(request))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/audio")
    public ResponseEntity<?> audio(@PathVariable String projectId, @PathVariable String id, HttpServletRequest request) {
        return transcriptionService.openAudio(projectId, id, authenticatedUser(request), isAdmin(request))
                .map(audio -> {
                    String fileName = audio.fileName() == null ? "recording" : audio.fileName();
                    String encoded = java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encoded)
                            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                            .header("X-Content-Type-Options", "nosniff")
                            .contentType(MediaType.parseMediaType(audio.contentType()))
                            .body(audio.resource());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String projectId, @PathVariable String id, HttpServletRequest request) {
        if (transcriptionService.delete(projectId, id, authenticatedUser(request), isAdmin(request))) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(ProjectMemberService.ProjectMemberAccessDeniedException.class)
    public ResponseEntity<Void> handleProjectMemberAccessDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler(ProjectMemberService.ProjectNotFoundException.class)
    public ResponseEntity<Void> handleProjectNotFound() {
        return ResponseEntity.notFound().build();
    }

    private AuthenticatedUser authenticatedUser(HttpServletRequest request) {
        Object value = request.getAttribute(SupabaseAuthenticationFilter.AUTHENTICATED_USER_ATTRIBUTE);
        if (value instanceof AuthenticatedUser user) {
            return user;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user is not resolved.");
    }

    private boolean isAdmin(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(SupabaseAuthenticationFilter.ADMIN_TEST_USER_ATTRIBUTE));
    }
}
