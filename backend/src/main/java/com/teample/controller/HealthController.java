package com.teample.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 배포 플랫폼(Railway 등)의 헬스체크용. 인증 없이 200을 돌려주며 내부 정보는 담지 않는다.
 * 보호 경로 목록은 {@link com.teample.security.SupabaseAuthenticationFilter}에 명시되어 있고 이 경로는 포함하지 않는다.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
