package com.teample.service.transcription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.teample.entity.TranscriptSegment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 리턴제로(RTZR) STT OpenAPI 연동.
 * 인증: POST /v1/authenticate (form) → access_token, expire_at
 * 제출: POST /v1/transcribe (multipart: file, config) → id
 * 조회: GET /v1/transcribe/{id} → status(transcribing|completed|failed), results.utterances[]
 */
@Component
public class RtzrSpeechToTextProvider implements SpeechToTextProvider {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("mp4", "m4a", "mp3", "amr", "flac", "wav");
    private static final Duration TOKEN_SAFETY_MARGIN = Duration.ofMinutes(2);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;
    private final String modelName;

    private volatile String accessToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public RtzrSpeechToTextProvider(
            @Value("${stt.rtzr.base-url:https://openapi.vito.ai}") String baseUrl,
            @Value("${stt.rtzr.client-id:}") String clientId,
            @Value("${stt.rtzr.client-secret:}") String clientSecret,
            @Value("${stt.rtzr.model-name:sommers}") String modelName
    ) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.modelName = modelName == null || modelName.isBlank() ? "sommers" : modelName.trim();
    }

    @Override
    public String name() {
        return "rtzr";
    }

    @Override
    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    @Override
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    @Override
    public String submit(Path file, String fileName, TranscriptionOptions options) throws SpeechToTextException {
        String token = ensureToken();
        String boundary = "----teample" + UUID.randomUUID().toString().replace("-", "");
        HttpRequest.BodyPublisher body;
        try {
            body = buildMultipartBody(boundary, file, fileName, buildConfig(options));
        } catch (IOException e) {
            throw new SpeechToTextException("전사 요청 본문을 만들지 못했습니다: " + e.getMessage(), e);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/transcribe"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofMinutes(10))
                .POST(body)
                .build();

        JsonNode root = send(request, "전사 요청");
        String jobId = root.path("id").asText("");
        if (jobId.isBlank()) {
            throw new SpeechToTextException("STT 응답에 작업 ID가 없습니다.");
        }
        return jobId;
    }

    @Override
    public PollResult poll(String jobId) throws SpeechToTextException {
        String token = ensureToken();
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/transcribe/" + jobId))
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        JsonNode root = send(request, "전사 상태 조회");
        return parsePollResponse(root);
    }

    PollResult parsePollResponse(JsonNode root) {
        String status = root.path("status").asText("").trim().toLowerCase(Locale.ROOT);
        switch (status) {
            case "completed" -> {
                List<TranscriptSegment> segments = new ArrayList<>();
                long maxEnd = 0;
                for (JsonNode utterance : root.path("results").path("utterances")) {
                    long start = utterance.path("start_at").asLong(0);
                    long duration = utterance.path("duration").asLong(0);
                    String text = utterance.path("msg").asText("").trim();
                    if (text.isEmpty()) {
                        continue;
                    }
                    String speaker = utterance.hasNonNull("spk") ? utterance.path("spk").asText() : "0";
                    long end = start + Math.max(duration, 0);
                    maxEnd = Math.max(maxEnd, end);
                    segments.add(new TranscriptSegment(speaker, start, end, text));
                }
                return PollResult.completed(segments, maxEnd > 0 ? maxEnd : null);
            }
            case "failed" -> {
                String message = root.path("error").isMissingNode()
                        ? "STT 처리에 실패했습니다."
                        : root.path("error").toString();
                return PollResult.failed(message);
            }
            default -> {
                return PollResult.processing();
            }
        }
    }

    private String buildConfig(TranscriptionOptions options) throws SpeechToTextException {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("model_name", modelName);
        config.put("language", options.language() == null || options.language().isBlank() ? "ko" : options.language());
        config.put("use_diarization", true);
        ObjectNode diarization = config.putObject("diarization");
        diarization.put("spk_count", options.expectedSpeakers() == null ? 0 : Math.max(options.expectedSpeakers(), 0));
        config.put("use_itn", true);
        config.put("use_disfluency_filter", true);
        config.put("use_paragraph_splitter", false);
        try {
            return objectMapper.writeValueAsString(config);
        } catch (IOException e) {
            throw new SpeechToTextException("STT 설정을 직렬화하지 못했습니다.", e);
        }
    }

    /** 파일을 메모리에 올리지 않고 head + 파일 스트림 + tail을 이어서 보낸다. */
    HttpRequest.BodyPublisher buildMultipartBody(String boundary, Path file, String fileName, String config) throws IOException {
        String safeName = fileName == null || fileName.isBlank() ? file.getFileName().toString() : fileName;
        safeName = safeName.replace("\"", "").replace("\r", "").replace("\n", "");
        if (!Files.isRegularFile(file)) {
            throw new IOException("전송할 파일이 없습니다: " + file.getFileName());
        }

        StringBuilder head = new StringBuilder();
        head.append("--").append(boundary).append("\r\n");
        head.append("Content-Disposition: form-data; name=\"config\"\r\n");
        head.append("Content-Type: application/json; charset=utf-8\r\n\r\n");
        head.append(config).append("\r\n");
        head.append("--").append(boundary).append("\r\n");
        head.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(safeName).append("\"\r\n");
        head.append("Content-Type: application/octet-stream\r\n\r\n");
        byte[] headBytes = head.toString().getBytes(StandardCharsets.UTF_8);
        byte[] tailBytes = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        return HttpRequest.BodyPublishers.concat(
                HttpRequest.BodyPublishers.ofByteArray(headBytes),
                HttpRequest.BodyPublishers.ofFile(file),
                HttpRequest.BodyPublishers.ofByteArray(tailBytes)
        );
    }

    private synchronized String ensureToken() throws SpeechToTextException {
        if (!isConfigured()) {
            throw new SpeechToTextException("STT 자격 증명이 설정되지 않았습니다.");
        }
        if (accessToken != null && Instant.now().plus(TOKEN_SAFETY_MARGIN).isBefore(tokenExpiresAt)) {
            return accessToken;
        }
        String form = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/authenticate"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        JsonNode root = send(request, "STT 인증");
        String token = root.path("access_token").asText("");
        if (token.isBlank()) {
            throw new SpeechToTextException("STT 인증 응답에 토큰이 없습니다.");
        }
        long expireAt = root.path("expire_at").asLong(0);
        accessToken = token;
        tokenExpiresAt = expireAt > 0
                ? Instant.ofEpochSecond(expireAt)
                : Instant.now().plus(Duration.ofHours(5));
        return accessToken;
    }

    private JsonNode send(HttpRequest request, String action) throws SpeechToTextException {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new SpeechToTextException(action + " 중 네트워크 오류: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpeechToTextException(action + "이(가) 중단되었습니다.", e);
        }
        if (response.statusCode() == 401) {
            accessToken = null;
            tokenExpiresAt = Instant.EPOCH;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new SpeechToTextException(action + " 실패 (HTTP " + response.statusCode() + "): "
                    + abbreviate(response.body()));
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new SpeechToTextException(action + " 응답을 해석하지 못했습니다.", e);
        }
    }

    private String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.trim();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) + "..." : trimmed;
    }
}
