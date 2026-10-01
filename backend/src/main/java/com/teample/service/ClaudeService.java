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
import java.util.Locale;
import java.util.Optional;

@Service
public class ClaudeService {

    private static final long MAX_OUTPUT_TOKENS = 8192L;
    static final int MAX_SHORT_TEXT = 255;
    private static final String MAX_TOKENS_STOP_REASON = "max_tokens";

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

    public enum SourceKind {
        /** 카카오톡 등 텍스트 대화 */
        CHAT,
        /** 녹음·영상을 전사한 텍스트. 각 줄이 "[mm:ss] 이름: 발언" 형식 */
        TRANSCRIPT
    }

    public MinutesResult analyze(String rawText, String subject, List<String> members) {
        return analyze(rawText, subject, members, SourceKind.CHAT);
    }

    public MinutesResult analyze(String rawText, String subject, List<String> members, SourceKind sourceKind) {
        if (client == null) {
            throw new RuntimeException("Anthropic API 키가 설정되지 않아 회의록을 생성할 수 없습니다.");
        }
        String prompt = buildPrompt(rawText, subject, members, sourceKind == null ? SourceKind.CHAT : sourceKind);

        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_SONNET_4_5)
                .maxTokens(MAX_OUTPUT_TOKENS)
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
        if (isMaxTokensStopReason(message)) {
            throw new RuntimeException("Claude 응답이 최대 토큰 제한에 도달해 잘렸습니다. 대화 내용을 줄여 다시 시도해 주세요.");
        }

        return parseResponse(responseText);
    }

    private boolean isMaxTokensStopReason(Message message) {
        Object stopReason = message.stopReason();
        if (stopReason instanceof Optional<?> optional) {
            stopReason = optional.orElse(null);
        }
        return MAX_TOKENS_STOP_REASON.equals(normalizeStopReason(stopReason));
    }

    private String normalizeStopReason(Object stopReason) {
        if (stopReason == null) {
            return "";
        }
        try {
            Object value = stopReason.getClass().getMethod("asString").invoke(stopReason);
            return String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return String.valueOf(stopReason).trim().toLowerCase(Locale.ROOT);
        }
    }

    String buildPrompt(String rawText, String subject, List<String> members, SourceKind sourceKind) {
        String intro = sourceKind == SourceKind.TRANSCRIPT
                ? """
                다음은 '%s' 수업의 팀 프로젝트 회의를 녹음해 음성 인식으로 전사한 내용입니다.
                각 줄은 "[분:초] 화자 이름: 발언" 형식이며, 음성 인식 특성상 오타나 끊긴 문장이 있을 수 있습니다.
                "화자 1"처럼 이름이 정해지지 않은 화자는 팀원 목록과 문맥으로 추정하되, 확실하지 않으면 그대로 두세요.
                팀원: %s

                이 회의 내용을 분석하여 회의록을 JSON 형식으로 생성해주세요."""
                : """
                다음은 '%s' 수업의 팀 프로젝트 카카오톡 대화 내용입니다.
                팀원: %s

                이 대화를 분석하여 회의록을 JSON 형식으로 생성해주세요.""";
        String sourceLabel = sourceKind == SourceKind.TRANSCRIPT ? "회의 전사 내용" : "카카오톡 대화";
        return intro.formatted(subject, String.join(", ", members)) + """

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
                - evidence에는 반드시 아래 원문에 실제로 존재하는 짧은 문장을 그대로 인용 (타임스탬프와 화자 이름은 빼고 발언만)
                - evidence 배열은 대응하는 결과 배열과 길이와 순서를 동일하게 유지
                - 근거를 찾을 수 없는 항목의 evidence 값은 빈 문자열 사용

                %s:
                %s
                """.formatted(sourceLabel, rawText);
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

            String title = truncate(root.path("title").asText(""), MAX_SHORT_TEXT);
            String topic = truncate(root.path("topic").asText(""), MAX_SHORT_TEXT);
            List<String> discussions = jsonArrayToList(root.path("discussions"));
            List<String> decisions = jsonArrayToList(root.path("decisions"));
            List<String> pending = jsonArrayToList(root.path("pending"));
            List<String> nextAgenda = jsonArrayToList(root.path("nextAgenda"));

            List<TodoData> todos = new ArrayList<>();
            for (JsonNode node : root.path("todos")) {
                todos.add(new TodoData(
                        TodoIdentity.newId(),
                        truncate(node.path("name").asText(""), MAX_SHORT_TEXT),
                        node.path("task").asText(""),
                        truncate(node.path("deadline").asText("미정"), MAX_SHORT_TEXT)
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

            return new MinutesResult(title, topic, discussions, decisions, pending, todos, nextAgenda, evidence);
        } catch (Exception e) {
            throw new RuntimeException("Claude 응답 파싱 실패: " + e.getMessage(), e);
        }
    }

    /** DB 컬럼(VARCHAR 255)을 넘지 않도록 자른다. AI가 긴 문장을 돌려줘도 저장 단계에서 500이 나지 않게 한다. */
    static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
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
