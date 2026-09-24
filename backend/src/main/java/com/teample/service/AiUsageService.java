package com.teample.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** Admission is serialized in the database, including across application replicas. */
@Service
@RequiredArgsConstructor
public class AiUsageService {
    private final JdbcTemplate jdbc;
    @Value("${ai.daily-limit:20}") private int dailyLimit = 20;
    @Value("${ai.concurrent-limit:4}") private int concurrentLimit = 4;

    @Transactional
    public String claim(String userId, String key, String fingerprint) {
        jdbc.queryForObject("select id from ai_admission_lock where id = 1 for update", Integer.class);
        Instant now = Instant.now();
        jdbc.update("update ai_requests set status = 'FAILED' where status = 'PENDING' and created_at < ?",
                Timestamp.from(now.minusSeconds(300)));
        List<String[]> existing = jdbc.query("select fingerprint, status, minutes_id from ai_requests where user_id = ? and request_key = ?",
                (rs, row) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)}, userId, key);
        if (!existing.isEmpty()) {
            String[] entry = existing.get(0);
            if (!entry[0].equals(fingerprint)) throw error(HttpStatus.CONFLICT, "동일한 요청 키에 다른 내용을 보낼 수 없습니다.");
            if (entry[1].equals("COMPLETED")) return entry[2];
            if (entry[1].equals("PENDING")) throw error(HttpStatus.CONFLICT, "회의록을 생성 중입니다. 잠시 후 다시 확인해 주세요.");
            throw error(HttpStatus.GONE, "실패한 생성 요청입니다. 입력을 확인하고 새 요청으로 다시 시도해 주세요.");
        }
        if (count("select count(*) from ai_requests where user_id = ? and created_at >= ?", userId, Timestamp.from(now.minusSeconds(86400))) >= dailyLimit)
            throw error(HttpStatus.TOO_MANY_REQUESTS, "최근 24시간의 회의록 생성 한도에 도달했습니다.");
        if (count("select count(*) from ai_requests where status = 'PENDING' and user_id = ?", userId) > 0
                || count("select count(*) from ai_requests where status = 'PENDING'") >= concurrentLimit)
            throw error(HttpStatus.TOO_MANY_REQUESTS, "생성 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
        jdbc.update("insert into ai_requests(user_id, request_key, fingerprint, status, created_at) values (?, ?, ?, 'PENDING', ?)",
                userId, key, fingerprint, Timestamp.from(now));
        return null;
    }

    @Transactional
    public void complete(String userId, String key, String minutesId) {
        int changed = jdbc.update("update ai_requests set status = 'COMPLETED', minutes_id = ? where user_id = ? and request_key = ? and status = 'PENDING'",
                minutesId, userId, key);
        if (changed != 1) throw error(HttpStatus.CONFLICT, "생성 요청이 만료되었습니다.");
    }

    @Transactional
    public void fail(String userId, String key) {
        jdbc.update("update ai_requests set status = 'FAILED' where user_id = ? and request_key = ? and status = 'PENDING'", userId, key);
    }

    private long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
