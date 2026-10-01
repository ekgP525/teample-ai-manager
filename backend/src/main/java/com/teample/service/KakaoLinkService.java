package com.teample.service;

import com.teample.dto.kakao.KakaoConnectUrlResponse;
import com.teample.dto.kakao.KakaoLinkResponse;
import com.teample.entity.KakaoLink;
import com.teample.repository.KakaoLinkRepository;
import com.teample.security.AuthenticatedUser;
import com.teample.security.SecretCipher;
import com.teample.service.kakao.KakaoApiClient;
import com.teample.service.kakao.KakaoApiClient.KakaoApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 카카오 "나에게 보내기" 연결 관리. 토큰 저장·갱신과 메시지 발송을 담당한다.
 */
@Service
public class KakaoLinkService {

    private static final long ACCESS_REFRESH_MARGIN_SECONDS = 300;
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final int STATE_BYTES = 24;

    private final KakaoLinkRepository kakaoLinkRepository;
    private final KakaoApiClient kakaoApiClient;
    private final SecretCipher secretCipher;
    private final SecureRandom secureRandom = new SecureRandom();
    /** 사용자별로 발급한 OAuth state. 콜백에서 한 번 쓰면 지운다. */
    private final Map<String, PendingState> pendingStates = new ConcurrentHashMap<>();

    public KakaoLinkService(KakaoLinkRepository kakaoLinkRepository, KakaoApiClient kakaoApiClient, SecretCipher secretCipher) {
        this.kakaoLinkRepository = kakaoLinkRepository;
        this.kakaoApiClient = kakaoApiClient;
        this.secretCipher = secretCipher;
    }

    @Transactional(readOnly = true)
    public KakaoLinkResponse status(AuthenticatedUser user) {
        requireUser(user);
        boolean configured = kakaoApiClient.isConfigured();
        return kakaoLinkRepository.findById(user.authUserId())
                .map(link -> KakaoLinkResponse.from(link, configured, link.isRefreshTokenExpired(LocalDateTime.now())))
                .orElseGet(() -> KakaoLinkResponse.notLinked(configured));
    }

    /** 카카오 동의 화면 URL. state는 요청마다 새로 만든 난수이며 서버가 10분 동안 기억한다. */
    public KakaoConnectUrlResponse connectUrl(AuthenticatedUser user, String redirectUri) {
        requireUser(user);
        ensureConfigured();
        validateRedirectUri(redirectUri);
        String state = issueState(user.authUserId());
        return new KakaoConnectUrlResponse(kakaoApiClient.buildAuthorizeUrl(redirectUri, state), state);
    }

    @Transactional
    public KakaoLinkResponse link(AuthenticatedUser user, String code, String redirectUri, String state) {
        requireUser(user);
        ensureConfigured();
        validateRedirectUri(redirectUri);
        consumeState(user.authUserId(), state);

        KakaoApiClient.TokenResponse token;
        String kakaoUserId;
        try {
            token = kakaoApiClient.exchangeCode(code, redirectUri);
            kakaoUserId = kakaoApiClient.fetchKakaoUserId(token.accessToken());
        } catch (KakaoApiException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "카카오 연결에 실패했습니다: " + e.getMessage(), e);
        }
        if (token.scope() != null && !token.scope().contains(KakaoApiClient.SCOPE_TALK_MESSAGE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "카카오톡 메시지 전송 동의가 필요합니다. 동의 화면에서 '카카오톡 메시지 전송'을 허용해 주세요.");
        }

        KakaoLink link = kakaoLinkRepository.findById(user.authUserId())
                .orElseGet(() -> KakaoLink.builder().userId(user.authUserId()).deadlineReminders(true).build());
        applyToken(link, token);
        link.setKakaoUserId(kakaoUserId);
        KakaoLink saved = kakaoLinkRepository.save(link);
        return KakaoLinkResponse.from(saved, true, false);
    }

    @Transactional
    public KakaoLinkResponse updatePreferences(AuthenticatedUser user, boolean deadlineReminders) {
        requireUser(user);
        KakaoLink link = kakaoLinkRepository.findById(user.authUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "카카오가 연결되어 있지 않습니다."));
        link.setDeadlineReminders(deadlineReminders);
        KakaoLink saved = kakaoLinkRepository.save(link);
        return KakaoLinkResponse.from(saved, kakaoApiClient.isConfigured(), saved.isRefreshTokenExpired(LocalDateTime.now()));
    }

    @Transactional
    public void unlink(AuthenticatedUser user) {
        requireUser(user);
        Optional<KakaoLink> link = kakaoLinkRepository.findById(user.authUserId());
        if (link.isEmpty()) {
            return;
        }
        try {
            kakaoApiClient.unlink(validAccessToken(link.get()));
        } catch (KakaoApiException | ResponseStatusException ignored) {
            // 카카오 쪽 연결 끊기가 실패해도 우리 쪽 토큰은 지운다.
        }
        kakaoLinkRepository.delete(link.get());
    }

    /**
     * 사용자 본인에게 카카오톡 메시지를 보낸다. 토큰이 만료됐으면 갱신 후 재시도한다.
     * 갱신 실패로 refresh token을 비운 상태 변경은 409를 던져도 커밋되어야 하므로 ResponseStatusException은 롤백하지 않는다.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void sendToSelf(String userId, String text, String webUrl, String buttonTitle) {
        KakaoLink link = kakaoLinkRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "카카오가 연결되어 있지 않습니다."));
        String accessToken = validAccessToken(link);
        try {
            kakaoApiClient.sendMemo(accessToken, text, webUrl, buttonTitle);
        } catch (KakaoApiException e) {
            if (e.isTokenInvalid()) {
                accessToken = forceRefresh(link);
                try {
                    kakaoApiClient.sendMemo(accessToken, text, webUrl, buttonTitle);
                } catch (KakaoApiException retry) {
                    throw toStatusException(retry);
                }
            } else {
                throw toStatusException(e);
            }
        }
        link.setLastNotifiedAt(LocalDateTime.now());
        kakaoLinkRepository.save(link);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void sendTestMessage(AuthenticatedUser user, String appBaseUrl) {
        requireUser(user);
        sendToSelf(user.authUserId(),
                "[팀플 AI] 카카오톡 알림이 연결되었습니다. 업무 마감 전날 이 채팅으로 알려 드릴게요.",
                appBaseUrl + "/dashboard", "대시보드 열기");
    }

    private String validAccessToken(KakaoLink link) {
        LocalDateTime now = LocalDateTime.now();
        if (link.getAccessExpiresAt() != null && link.getAccessExpiresAt().isAfter(now.plusSeconds(ACCESS_REFRESH_MARGIN_SECONDS))) {
            return secretCipher.decrypt(link.getAccessToken());
        }
        return forceRefresh(link);
    }

    private String forceRefresh(KakaoLink link) {
        if (link.isRefreshTokenExpired(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "카카오 연결이 만료되었습니다. 프로필에서 다시 연결해 주세요.");
        }
        try {
            KakaoApiClient.TokenResponse token = kakaoApiClient.refresh(secretCipher.decrypt(link.getRefreshToken()));
            applyToken(link, token);
            kakaoLinkRepository.save(link);
            return token.accessToken();
        } catch (KakaoApiException e) {
            if (e.isInvalidGrant()) {
                // 리프레시 토큰이 카카오 쪽에서 무효가 됐다. 다시 연결해야 하므로 needsReconnect가 되도록 지운다.
                link.setRefreshToken(null);
                link.setRefreshExpiresAt(null);
                kakaoLinkRepository.save(link);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "카카오 연결이 만료되었습니다. 프로필에서 다시 연결해 주세요.", e);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "카카오 토큰 갱신에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private void applyToken(KakaoLink link, KakaoApiClient.TokenResponse token) {
        LocalDateTime now = LocalDateTime.now();
        link.setAccessToken(secretCipher.encrypt(token.accessToken()));
        link.setAccessExpiresAt(now.plusSeconds(Math.max(token.expiresInSeconds(), 60)));
        if (token.refreshToken() != null && !token.refreshToken().isBlank()) {
            link.setRefreshToken(secretCipher.encrypt(token.refreshToken()));
            if (token.refreshExpiresInSeconds() != null && token.refreshExpiresInSeconds() > 0) {
                link.setRefreshExpiresAt(now.plusSeconds(token.refreshExpiresInSeconds()));
            }
        }
        if (token.scope() != null && !token.scope().isBlank()) {
            link.setScopes(token.scope());
        }
    }

    private ResponseStatusException toStatusException(KakaoApiException e) {
        if (e.isScopeMissing()) {
            return new ResponseStatusException(HttpStatus.CONFLICT,
                    "카카오톡 메시지 전송 동의가 없습니다. 프로필에서 카카오를 다시 연결해 주세요.");
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
    }

    private String issueState(String userId) {
        Instant now = Instant.now();
        pendingStates.values().removeIf(pending -> pending.isExpired(now));
        byte[] bytes = new byte[STATE_BYTES];
        secureRandom.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pendingStates.put(userId, new PendingState(nonce, now.plus(STATE_TTL)));
        return nonce;
    }

    /** state가 없거나, 발급한 적이 없거나, 만료됐거나, 다르면 400. 맞으면 소비해서 재사용을 막는다. */
    private void consumeState(String userId, String state) {
        if (state == null || state.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "카카오 연결 요청에 state가 없습니다. 연결을 다시 시작해 주세요.");
        }
        PendingState pending = pendingStates.get(userId);
        if (pending == null || pending.isExpired(Instant.now()) || !pending.nonce().equals(state.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "카카오 연결 요청이 현재 사용자와 일치하지 않거나 만료되었습니다. 연결을 다시 시작해 주세요.");
        }
        pendingStates.remove(userId, pending);
    }

    private record PendingState(String nonce, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return !expiresAt.isAfter(now);
        }
    }

    private void validateRedirectUri(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()
                || !(redirectUri.startsWith("http://") || redirectUri.startsWith("https://"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "redirectUri가 올바르지 않습니다.");
        }
    }

    private void ensureConfigured() {
        if (!kakaoApiClient.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "카카오 알림이 아직 설정되지 않았습니다. KAKAO_REST_API_KEY를 확인해 주세요.");
        }
    }

    private void requireUser(AuthenticatedUser user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user is not resolved.");
        }
    }
}
