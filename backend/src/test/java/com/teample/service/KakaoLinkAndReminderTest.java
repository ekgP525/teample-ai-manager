package com.teample.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teample.dto.kakao.KakaoLinkResponse;
import com.teample.entity.IntegratedTodo;
import com.teample.entity.KakaoLink;
import com.teample.entity.Project;
import com.teample.entity.ProjectMember;
import com.teample.entity.ProjectMemberRole;
import com.teample.entity.ProjectStatus;
import com.teample.entity.TodoStatus;
import com.teample.repository.IntegratedTodoRepository;
import com.teample.repository.KakaoLinkRepository;
import com.teample.repository.NotificationLogRepository;
import com.teample.repository.ProjectMemberRepository;
import com.teample.security.AuthenticatedUser;
import com.teample.security.SecretCipher;
import com.teample.service.kakao.KakaoApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KakaoLinkAndReminderTest {

    private final AuthenticatedUser user = new AuthenticatedUser("user-1", "박규남", null);

    @Test
    void secretCipherRoundTripsAndFallsBackToPlainWithoutKey() {
        SecretCipher withKey = new SecretCipher("some-long-random-key");
        String stored = withKey.encrypt("token-123");
        assertThat(stored).startsWith("enc:");
        assertThat(withKey.decrypt(stored)).isEqualTo("token-123");

        SecretCipher noKey = new SecretCipher("");
        assertThat(noKey.isEncryptionEnabled()).isFalse();
        assertThat(noKey.decrypt(noKey.encrypt("abc"))).isEqualTo("abc");
        assertThat(noKey.decrypt("legacy-plain")).isEqualTo("legacy-plain");
    }

    @Test
    void secretCipherRefusesToStartWithoutKeyWhenKakaoIsConfigured() {
        assertThatThrownBy(() -> new SecretCipher("", "kakao-rest-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_TOKEN_ENCRYPTION_KEY");
        assertThat(new SecretCipher("some-key", "kakao-rest-key").isEncryptionEnabled()).isTrue();
        assertThat(new SecretCipher("", "").isEncryptionEnabled()).isFalse();
    }

    @Test
    void kakaoTokenParsingAndAuthorizeUrl() throws Exception {
        KakaoApiClient client = new KakaoApiClient("https://kauth.kakao.com", "https://kapi.kakao.com", "rest-key", "");
        String url = client.buildAuthorizeUrl("http://localhost:3000/kakao/callback", "st");

        assertThat(client.isConfigured()).isTrue();
        assertThat(url).startsWith("https://kauth.kakao.com/oauth/authorize?client_id=rest-key");
        assertThat(url).contains("redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Fkakao%2Fcallback");
        assertThat(url).contains("scope=talk_message").contains("state=st");

        KakaoApiClient.TokenResponse token = client.parseToken(new ObjectMapper().readTree(
                "{\"access_token\":\"at\",\"expires_in\":43199,\"refresh_token\":\"rt\",\"refresh_token_expires_in\":5184000,\"scope\":\"talk_message\"}"));
        assertThat(token.accessToken()).isEqualTo("at");
        assertThat(token.refreshToken()).isEqualTo("rt");
        assertThat(token.refreshExpiresInSeconds()).isEqualTo(5184000L);
    }

    @Test
    void linkStoresEncryptedTokensAndRequiresTalkMessageScope() throws Exception {
        KakaoLinkRepository links = mock(KakaoLinkRepository.class);
        KakaoApiClient api = mock(KakaoApiClient.class);
        when(api.isConfigured()).thenReturn(true);
        when(links.findById("user-1")).thenReturn(Optional.empty());
        when(links.save(any(KakaoLink.class))).thenAnswer(inv -> inv.getArgument(0));
        SecretCipher cipher = new SecretCipher("k");
        when(api.buildAuthorizeUrl(anyString(), anyString())).thenAnswer(inv -> "https://kauth.kakao.com/oauth/authorize?state=" + inv.getArgument(1));
        KakaoLinkService service = new KakaoLinkService(links, api, cipher);
        String redirectUri = "http://localhost:3000/kakao/callback";
        String state = service.connectUrl(user, redirectUri).state();
        assertThat(state).isNotBlank();
        assertThat(service.connectUrl(user, redirectUri).state()).isNotEqualTo(state);
        state = service.connectUrl(user, redirectUri).state();

        when(api.exchangeCode("code", redirectUri))
                .thenReturn(new KakaoApiClient.TokenResponse("at", 43199, "rt", 5184000L, "talk_message profile"));
        when(api.fetchKakaoUserId("at")).thenReturn("9876");

        KakaoLinkResponse response = service.link(user, "code", redirectUri, state);

        assertThat(response.linked()).isTrue();
        assertThat(response.deadlineReminders()).isTrue();
        verify(links).save(any(KakaoLink.class));

        // state는 한 번 쓰면 소비된다.
        String usedState = state;
        assertThatThrownBy(() -> service.link(user, "code", redirectUri, usedState))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        String fresh = service.connectUrl(user, redirectUri).state();
        assertThatThrownBy(() -> service.link(user, "code", redirectUri, "wrong-state"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> service.link(user, "code", redirectUri, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> service.link(new AuthenticatedUser("user-2", "다른", null), "code", redirectUri, fresh))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        when(api.exchangeCode("code2", redirectUri))
                .thenReturn(new KakaoApiClient.TokenResponse("at2", 43199, "rt2", 5184000L, "profile"));
        when(api.fetchKakaoUserId("at2")).thenReturn("1");
        assertThatThrownBy(() -> service.link(user, "code2", redirectUri, fresh))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("메시지 전송 동의");
    }

    @Test
    void invalidGrantOnRefreshClearsRefreshTokenAndAsksToReconnect() throws Exception {
        KakaoLinkRepository links = mock(KakaoLinkRepository.class);
        KakaoApiClient api = mock(KakaoApiClient.class);
        SecretCipher cipher = new SecretCipher("");
        KakaoLink link = KakaoLink.builder()
                .userId("user-1")
                .accessToken(cipher.encrypt("old"))
                .refreshToken(cipher.encrypt("rt"))
                .accessExpiresAt(LocalDateTime.now().minusMinutes(1))
                .refreshExpiresAt(LocalDateTime.now().plusDays(30))
                .deadlineReminders(true)
                .build();
        when(links.findById("user-1")).thenReturn(Optional.of(link));
        when(links.save(any(KakaoLink.class))).thenAnswer(inv -> inv.getArgument(0));
        when(api.refresh("rt")).thenThrow(new KakaoApiClient.KakaoApiException(
                "카카오 토큰 갱신 실패 (invalid_grant) refresh token expired", 400, "invalid_grant"));
        KakaoLinkService service = new KakaoLinkService(links, api, cipher);

        assertThatThrownBy(() -> service.sendToSelf("user-1", "hello", "http://localhost:3000/dashboard", "열기"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("다시 연결");

        assertThat(link.getRefreshToken()).isNull();
        assertThat(link.getRefreshExpiresAt()).isNull();
        assertThat(link.isRefreshTokenExpired(LocalDateTime.now())).isTrue();
        verify(links).save(link);
        verify(api, never()).sendMemo(anyString(), anyString(), anyString(), anyString());
        assertThat(new KakaoApiClient.KakaoApiException("x", 400, "KOE319").isInvalidGrant()).isTrue();
        assertThat(new KakaoApiClient.KakaoApiException("x", 500, "-1").isInvalidGrant()).isFalse();
    }

    @Test
    void sendToSelfRefreshesExpiredAccessTokenFirst() throws Exception {
        KakaoLinkRepository links = mock(KakaoLinkRepository.class);
        KakaoApiClient api = mock(KakaoApiClient.class);
        SecretCipher cipher = new SecretCipher("");
        KakaoLink link = KakaoLink.builder()
                .userId("user-1")
                .accessToken(cipher.encrypt("old"))
                .refreshToken(cipher.encrypt("rt"))
                .accessExpiresAt(LocalDateTime.now().minusMinutes(1))
                .refreshExpiresAt(LocalDateTime.now().plusDays(30))
                .deadlineReminders(true)
                .build();
        when(links.findById("user-1")).thenReturn(Optional.of(link));
        when(links.save(any(KakaoLink.class))).thenAnswer(inv -> inv.getArgument(0));
        when(api.refresh("rt")).thenReturn(new KakaoApiClient.TokenResponse("fresh", 43199, null, null, ""));
        KakaoLinkService service = new KakaoLinkService(links, api, cipher);

        service.sendToSelf("user-1", "hello", "http://localhost:3000/dashboard", "열기");

        verify(api).sendMemo(eq("fresh"), eq("hello"), anyString(), anyString());
        verify(api, never()).sendMemo(eq("old"), anyString(), anyString(), anyString());
        assertThat(link.getLastNotifiedAt()).isNotNull();
        assertThat(cipher.decrypt(link.getAccessToken())).isEqualTo("fresh");
    }

    @Test
    void reminderFindsTodosDueTodayOrTomorrowAssignedToUserAndDedupes() {
        KakaoLinkRepository links = mock(KakaoLinkRepository.class);
        ProjectMemberRepository members = mock(ProjectMemberRepository.class);
        IntegratedTodoRepository todos = mock(IntegratedTodoRepository.class);
        NotificationLogRepository logs = mock(NotificationLogRepository.class);
        KakaoLinkService kakaoLinkService = mock(KakaoLinkService.class);
        Project project = Project.builder().id("p1").name("캡스톤").status(ProjectStatus.ACTIVE).build();
        LocalDate today = LocalDate.of(2026, 10, 1);

        when(links.findByDeadlineRemindersTrue()).thenReturn(List.of(
                KakaoLink.builder().userId("user-1").accessToken("x").deadlineReminders(true).build()));
        when(members.findByUserIdOrderByJoinedAtAsc("user-1")).thenReturn(List.of(
                ProjectMember.builder().project(project).userId("user-1").displayName("박규남").role(ProjectMemberRole.MEMBER).build()));
        when(todos.findByProjectIdAndStatusOrderByPriorityOrderAscCreatedAtAsc("p1", TodoStatus.TODO)).thenReturn(List.of(
                todo("발표 자료", "박규남", today),
                todo("데모 영상", "이다혜, 박규남", today.plusDays(1)),
                todo("보고서", "이다혜", today.plusDays(1)),
                todo("회고", "전체", today.plusDays(5)),
                todo("기획서", "박규남", null)
        ));
        when(logs.existsByUserIdAndChannelAndDedupeKey("user-1", "KAKAO", "deadline:2026-10-01")).thenReturn(false);
        DeadlineReminderService service = new DeadlineReminderService(links, members, todos, logs, kakaoLinkService, "http://localhost:3000/");

        List<DeadlineReminderService.DueTodo> due = service.findDueTodos("user-1", today);
        assertThat(due).extracting(DeadlineReminderService.DueTodo::content).containsExactly("발표 자료", "데모 영상");

        String message = service.buildMessage(due, today);
        assertThat(message).startsWith("[팀플 AI] 마감이 다가온 업무가 있어요.");
        assertThat(message).contains("• 오늘 10월 1일").contains("캡스톤 · 발표 자료");
        assertThat(message).contains("• 내일 10월 2일").contains("데모 영상");

        int sent = service.sendDueReminders(today);
        assertThat(sent).isEqualTo(1);
        verify(kakaoLinkService).sendToSelf(eq("user-1"), anyString(), eq("http://localhost:3000/dashboard"), anyString());
        verify(logs).save(any());

        when(logs.existsByUserIdAndChannelAndDedupeKey("user-1", "KAKAO", "deadline:2026-10-01")).thenReturn(true);
        assertThat(service.sendDueReminders(today)).isZero();
    }

    private static IntegratedTodo todo(String content, String assignee, LocalDate due) {
        IntegratedTodo todo = new IntegratedTodo();
        todo.setContent(content);
        todo.setAssigneeName(assignee);
        todo.setDueDate(due);
        todo.setStatus(TodoStatus.TODO);
        return todo;
    }
}
