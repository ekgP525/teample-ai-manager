package com.teample.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teample.entity.TodoData;
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
                    .build();
        }
    }

    public MinutesResult analyze(String rawText, String subject, List<String> members) {
        String prompt = buildPrompt(rawText, subject, members);

        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_SONNET_4_5)
                .maxTokens(2048L)
                .addUserMessage(prompt)
                .build();

        Message message = client.messages().create(params);

        StringBuilder sb = new StringBuilder();
        message.content().forEach(block ->
                block.text().ifPresent(textBlock -> sb.append(textBlock.text()))
        );

        String responseText = sb.toString();
        if (responseText.isBlank()) {
            throw new RuntimeException("Claude 응답이 비어있습니다.");
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
                  "topic": "회의 주제 (한 줄 요약)",
                  "discussions": ["주요 논의 내용 1", "주요 논의 내용 2", ...],
                  "decisions": ["최종 결정 사항 1", "최종 결정 사항 2", ...],
                  "pending": ["미결정 사항 1", "미결정 사항 2", ...],
                  "todos": [
                    {"name": "담당자 이름", "task": "업무 내용", "deadline": "마감일"},
                    ...
                  ],
                  "nextAgenda": ["다음 회의에서 확인할 내용 1", ...]
                }

                규칙:
                - 대화에서 언급된 내용만 기반으로 작성
                - 담당자 이름은 팀원 목록에서 매칭
                - 마감일은 대화에서 언급된 날짜 사용, 없으면 "미정"
                - 배열이 비어있으면 빈 배열 [] 사용

                카카오톡 대화:
                %s
                """.formatted(subject, String.join(", ", members), rawText);
    }

    private MinutesResult parseResponse(String responseText) {
        try {
            int start = responseText.indexOf('{');
            int end = responseText.lastIndexOf('}');
            if (start == -1 || end == -1) {
                throw new RuntimeException("JSON을 찾을 수 없습니다.");
            }
            String json = responseText.substring(start, end + 1);

            JsonNode root = objectMapper.readTree(json);

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

            return new MinutesResult(topic, discussions, decisions, pending, todos, nextAgenda);
        } catch (Exception e) {
            throw new RuntimeException("Claude 응답 파싱 실패: " + e.getMessage(), e);
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
            String topic,
            List<String> discussions,
            List<String> decisions,
            List<String> pending,
            List<TodoData> todos,
            List<String> nextAgenda
    ) {}
}
