package com.teample.service.transcription;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RtzrSpeechToTextProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RtzrSpeechToTextProvider provider =
            new RtzrSpeechToTextProvider("https://openapi.vito.ai/", "", "", "sommers");

    @Test
    void notConfiguredWithoutCredentials() {
        assertThat(provider.isConfigured()).isFalse();
        assertThat(new RtzrSpeechToTextProvider("https://x", "id", "secret", null).isConfigured()).isTrue();
    }

    @Test
    void parsesCompletedUtterancesIntoSegments() throws Exception {
        String json = """
                {
                  "id": "job-1",
                  "status": "completed",
                  "results": {
                    "utterances": [
                      {"start_at": 0, "duration": 1500, "spk": 0, "msg": "안녕하세요"},
                      {"start_at": 1600, "duration": 2400, "spk": 1, "msg": "네 시작하죠"},
                      {"start_at": 4000, "duration": 500, "spk": 0, "msg": "   "}
                    ]
                  }
                }
                """;

        SpeechToTextProvider.PollResult result = provider.parsePollResponse(objectMapper.readTree(json));

        assertThat(result.state()).isEqualTo(SpeechToTextProvider.PollState.COMPLETED);
        assertThat(result.segments()).hasSize(2);
        assertThat(result.segments().get(0).getSpeaker()).isEqualTo("0");
        assertThat(result.segments().get(0).getEndMs()).isEqualTo(1500);
        assertThat(result.segments().get(1).getSpeaker()).isEqualTo("1");
        assertThat(result.segments().get(1).getText()).isEqualTo("네 시작하죠");
        assertThat(result.durationMs()).isEqualTo(4000);
    }

    @Test
    void transcribingStatusIsStillProcessing() throws Exception {
        SpeechToTextProvider.PollResult result =
                provider.parsePollResponse(objectMapper.readTree("{\"id\":\"job\",\"status\":\"transcribing\"}"));

        assertThat(result.state()).isEqualTo(SpeechToTextProvider.PollState.PROCESSING);
    }

    @Test
    void failedStatusCarriesError() throws Exception {
        SpeechToTextProvider.PollResult result = provider.parsePollResponse(
                objectMapper.readTree("{\"id\":\"job\",\"status\":\"FAILED\",\"error\":{\"code\":\"E1\"}}"));

        assertThat(result.state()).isEqualTo(SpeechToTextProvider.PollState.FAILED);
        assertThat(result.error()).contains("E1");
    }
}
