package com.teample.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teample.entity.TodoData;
import com.teample.entity.EvidenceData;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ClaudeService {

    @Value("${anthropic.api-key:}")
    private String apiKey;

    private AnthropicClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        if (apiKey != null && !apiKey.isBlank()) {
            client = AnthropicOkHttpClient.builder()
                    .apiKey(apiKey)
                    .timeout(java.time.Duration.ofSeconds(90))
                    .maxRetries(0)
                    .build();
        }
    }

    public void ensureConfigured() {
        if (client == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "AI 서비스가 설정되지 않았습니다.");
    }

    public MinutesResult analyze(String rawText, String subject, List<String> members) {
        ensureConfigured();
        String prompt = buildPrompt(rawText, subject, members);

        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_SONNET_4_5)
                .maxTokens(8192L)
                .addUserMessage(prompt)
                .build();

        Message message;
        try { message = client.messages().create(params); }
        catch (RuntimeException e) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "AI 응답을 받지 못했습니다. 잠시 후 새 요청으로 다시 시도해 주세요.");
        }
        if (message.stopReason().map(reason -> reason.equals(StopReason.MAX_TOKENS)).orElse(false)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "AI 결과가 너무 깁니다. 대화를 나누어 다시 요청해 주세요.");
        }

        StringBuilder sb = new StringBuilder();
        message.content().forEach(block ->
                block.text().ifPresent(textBlock -> sb.append(textBlock.text()))
        );

        String responseText = sb.toString();
        if (responseText.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "AI 응답이 비어있습니다.");
        }

        return parseResponse(responseText);
    }

    private String buildPrompt(String rawText, String subject, List<String> members) {
        return """
                다음은 '%s' 수업의 팀 프로젝트 카카오톡 대화 내용입니다.
                팀원: %s

                이 대화를 분석하여 회의록을 JSON 형식으로 생성해주세요.
                반드시 아래 JSON 형식만 출력하고, 다른 텍스트는 포함하지 마세요.

                {
                  "title": "회의록 제목 (간결하고 핵심적인 제목, 예: 'UI 디자인 확정 회의')",
                  "topic": "회의 주제 (한 줄 요약)",
                  "discussions": ["주요 논의 내용 1", "주요 논의 내용 2", ...],
                  "decisions": ["최종 결정 사항 1", "최종 결정 사항 2", ...],
                  "pending": ["미결정 사항 1", "미결정 사항 2", ...],
                  "todos": [
                    {"name": "담당자 이름", "task": "업무 내용", "deadline": "마감일"},
                    ...
                  ],
                  "nextAgenda": ["다음 회의에서 확인할 내용 1", ...],
                  "evidence": {
                    "title": "제목의 근거가 된 원문 인용",
                    "topic": "주제의 근거가 된 원문 인용",
                    "discussions": ["각 discussions 항목과 같은 순서의 원문 인용"],
                    "decisions": ["각 decisions 항목과 같은 순서의 원문 인용"],
                    "pending": ["각 pending 항목과 같은 순서의 원문 인용"],
                    "todos": ["각 todos 항목과 같은 순서의 원문 인용"],
                    "nextAgenda": ["각 nextAgenda 항목과 같은 순서의 원문 인용"]
                  }
                }

                규칙:
                - 대화에서 언급된 내용만 기반으로 작성
                - 담당자 이름은 팀원 목록에서 매칭
                - 마감일은 대화에서 언급된 날짜 사용, 없으면 "미정"
                - 배열이 비어있으면 빈 배열 [] 사용
                - evidence에는 반드시 아래 대화 원문에 실제로 존재하는 짧은 문장을 그대로 인용
                - evidence 배열은 대응하는 결과 배열과 길이와 순서를 동일하게 유지
                - 근거를 찾을 수 없는 항목의 evidence 값은 빈 문자열 사용

                카카오톡 대화:
                %s
                """.formatted(subject, String.join(", ", members), rawText);
    }

    MinutesResult parseResponse(String responseText) {
        try {
            int start = responseText.indexOf('{');
            int end = responseText.lastIndexOf('}');
            if (start == -1 || end == -1) {
                throw new RuntimeException("JSON을 찾을 수 없습니다.");
            }
            String json = responseText.substring(start, end + 1);

            JsonNode root = objectMapper.readTree(json);

            String title = root.path("title").asText("");
            String topic = root.path("topic").asText("");
            List<String> discussions = jsonArrayToList(root.path("discussions"));
            List<String> decisions = jsonArrayToList(root.path("decisions"));
            List<String> pending = jsonArrayToList(root.path("pending"));
            List<String> nextAgenda = jsonArrayToList(root.path("nextAgenda"));

            List<TodoData> todos = new ArrayList<>();
            for (JsonNode node : root.path("todos")) {
                todos.add(new TodoData(
                        node.path("name").asText(""),
                        node.path("task").asText(""),
                        node.path("deadline").asText("미정")
                ));
            }

            JsonNode evidenceNode = root.path("evidence");
            EvidenceData evidence = new EvidenceData(
                    evidenceNode.path("title").asText(""),
                    evidenceNode.path("topic").asText(""),
                    jsonArrayToList(evidenceNode.path("discussions")),
                    jsonArrayToList(evidenceNode.path("decisions")),
                    jsonArrayToList(evidenceNode.path("pending")),
                    jsonArrayToList(evidenceNode.path("todos")),
                    jsonArrayToList(evidenceNode.path("nextAgenda"))
            );

            if (title.isBlank() || title.length() > 255 || topic.isBlank() || topic.length() > 255 || todos.size() > 100
                    || todos.stream().anyMatch(t -> t.getTask().isBlank() || t.getTask().length() > 4000 || t.getName().length() > 255 || t.getDeadline().length() > 255)) {
                throw new IllegalArgumentException("Invalid AI output");
            }
            return new MinutesResult(title, topic, discussions, decisions, pending, todos, nextAgenda, evidence);
        } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "AI 결과 형식이 올바르지 않습니다. 대화를 줄여 다시 요청해 주세요.");
        }
    }

    private List<String> jsonArrayToList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode.isArray()) {
            for (JsonNode node : arrayNode) {
                list.add(node.asText());
            }
        }
        return list;
    }

    public record MinutesResult(
            String title,
            String topic,
            List<String> discussions,
            List<String> decisions,
            List<String> pending,
            List<TodoData> todos,
            List<String> nextAgenda,
            EvidenceData evidence
    ) {}
}
