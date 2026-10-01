package com.teample.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 개발용 관리자 테스트 인증. 기본은 꺼져 있고(ADMIN_TEST_ENABLED=false),
 * 켜더라도 ID가 비어 있거나 비밀번호가 12자 미만이면 어떤 자격 증명도 통과하지 못한다.
 */
@Service
public class AdminTestAuthService {

    static final int MIN_PASSWORD_LENGTH = 12;
    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);

    private final boolean enabled;
    private final String adminId;
    private final String adminPassword;
    private final Map<String, Deque<Instant>> failuresByClient = new ConcurrentHashMap<>();

    public AdminTestAuthService(
            @Value("${admin.test-enabled:false}") boolean enabled,
            @Value("${admin.test-id:}") String adminId,
            @Value("${admin.test-password:}") String adminPassword
    ) {
        this.enabled = enabled;
        this.adminId = adminId == null ? "" : adminId.trim();
        this.adminPassword = adminPassword == null ? "" : adminPassword;
    }

    public boolean isEnabled() {
        return enabled && isUsable();
    }

    public boolean matches(String id, String password) {
        if (!isEnabled()) {
            return false;
        }
        return secureEquals(adminId, id) && secureEquals(adminPassword, password);
    }

    public String adminId() {
        return adminId;
    }

    /** 같은 클라이언트가 10분 안에 5번 이상 실패했으면 true. */
    public boolean isRateLimited(String clientKey) {
        Deque<Instant> failures = failuresByClient.get(normalizeClient(clientKey));
        if (failures == null) {
            return false;
        }
        synchronized (failures) {
            prune(failures, Instant.now());
            return failures.size() >= MAX_FAILED_ATTEMPTS;
        }
    }

    public void recordFailure(String clientKey) {
        Deque<Instant> failures = failuresByClient.computeIfAbsent(normalizeClient(clientKey), key -> new ArrayDeque<>());
        synchronized (failures) {
            Instant now = Instant.now();
            prune(failures, now);
            failures.addLast(now);
        }
    }

    public void clearFailures(String clientKey) {
        failuresByClient.remove(normalizeClient(clientKey));
    }

    private boolean isUsable() {
        return !adminId.isBlank() && adminPassword.length() >= MIN_PASSWORD_LENGTH;
    }

    private static void prune(Deque<Instant> failures, Instant now) {
        Instant threshold = now.minus(FAILURE_WINDOW);
        while (!failures.isEmpty() && failures.peekFirst().isBefore(threshold)) {
            failures.pollFirst();
        }
    }

    private static String normalizeClient(String clientKey) {
        return clientKey == null || clientKey.isBlank() ? "unknown" : clientKey.trim();
    }

    private boolean secureEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
