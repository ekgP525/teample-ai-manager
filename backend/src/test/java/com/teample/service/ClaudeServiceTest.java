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
}
