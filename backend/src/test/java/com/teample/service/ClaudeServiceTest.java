package com.teample.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeServiceTest {

    @Test
    void parsesEvidenceAlongsideBackwardCompatibleMinutesFields() {
        String response = """
                ```json
                {
                  "title": "Sprint planning",
                  "topic": "Release scope",
                  "discussions": ["Discussed login"],
                  "decisions": ["Ship Friday"],
                  "pending": [],
                  "todos": [{"name":"Kim","task":"QA","deadline":"Friday"}],
                  "nextAgenda": ["Review metrics"],
                  "evidence": {
                    "title": "Let's plan the sprint",
                    "topic": "release scope",
                    "discussions": ["We discussed login"],
                    "decisions": ["Ship it Friday"],
                    "pending": [],
                    "todos": ["Kim will QA by Friday"],
                    "nextAgenda": ["Next time, review metrics"]
                  }
                }
                ```
                """;

        ClaudeService.MinutesResult result = new ClaudeService().parseResponse(response);

        assertThat(result.title()).isEqualTo("Sprint planning");
        assertThat(result.evidence().getDecisions()).containsExactly("Ship it Friday");
        assertThat(result.evidence().getTodos()).containsExactly("Kim will QA by Friday");
    }

    @Test
    void acceptsLegacyAiResponseWithoutEvidence() {
        String response = """
                {"title":"Legacy","topic":"Topic","discussions":[],"decisions":[],
                 "pending":[],"todos":[],"nextAgenda":[]}
                """;

        ClaudeService.MinutesResult result = new ClaudeService().parseResponse(response);

        assertThat(result.evidence()).isNotNull();
        assertThat(result.evidence().getTitle()).isEmpty();
        assertThat(result.evidence().getDiscussions()).isEmpty();
    }

    @Test
    void transcriptPromptExplainsTimestampedSpeakerLines() {
        ClaudeService service = new ClaudeService();

        String transcript = service.buildPrompt("[00:05] 박규남: 시작하죠", "캡스톤", java.util.List.of("박규남", "이다혜"),
                ClaudeService.SourceKind.TRANSCRIPT);
        String chat = service.buildPrompt("규남: ㅎㅇ", "캡스톤", java.util.List.of("박규남"), ClaudeService.SourceKind.CHAT);

        assertThat(transcript).contains("음성 인식으로 전사한 내용");
        assertThat(transcript).contains("회의 전사 내용:\n[00:05] 박규남: 시작하죠");
        assertThat(transcript).contains("타임스탬프와 화자 이름은 빼고");
        assertThat(chat).contains("카카오톡 대화 내용");
        assertThat(chat).contains("카카오톡 대화:\n규남: ㅎㅇ");
    }
}
