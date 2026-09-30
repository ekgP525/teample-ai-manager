package com.teample.controller;

import com.teample.dto.kakao.KakaoConnectUrlResponse;
import com.teample.dto.kakao.KakaoLinkRequest;
import com.teample.dto.kakao.KakaoLinkResponse;
import com.teample.dto.kakao.KakaoPreferencesRequest;
import com.teample.security.AuthenticatedUser;
import com.teample.security.SupabaseAuthenticationFilter;
import com.teample.service.KakaoLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/me/kakao")
public class KakaoLinkController {

    private final KakaoLinkService kakaoLinkService;
    private final String frontendBaseUrl;

    public KakaoLinkController(
            KakaoLinkService kakaoLinkService,
            @Value("${app.frontend-base-url:http://localhost:3000}") String frontendBaseUrl
    ) {
        this.kakaoLinkService = kakaoLinkService;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    @GetMapping
    public ResponseEntity<KakaoLinkResponse> status(HttpServletRequest request) {
        return ResponseEntity.ok(kakaoLinkService.status(authenticatedUser(request)));
    }

    @GetMapping("/connect-url")
    public ResponseEntity<KakaoConnectUrlResponse> connectUrl(
            @RequestParam("redirectUri") String redirectUri, HttpServletRequest request
    ) {
        return ResponseEntity.ok(kakaoLinkService.connectUrl(authenticatedUser(request), redirectUri));
    }

    @PostMapping("/link")
    public ResponseEntity<KakaoLinkResponse> link(@Valid @RequestBody KakaoLinkRequest body, HttpServletRequest request) {
        return ResponseEntity.ok(kakaoLinkService.link(authenticatedUser(request), body.code(), body.redirectUri(), body.state()));
    }

    @PutMapping("/preferences")
    public ResponseEntity<KakaoLinkResponse> preferences(
            @Valid @RequestBody KakaoPreferencesRequest body, HttpServletRequest request
    ) {
        return ResponseEntity.ok(kakaoLinkService.updatePreferences(authenticatedUser(request), body.deadlineReminders()));
    }

    @PostMapping("/test")
    public ResponseEntity<Void> sendTest(HttpServletRequest request) {
        kakaoLinkService.sendTestMessage(authenticatedUser(request), frontendBaseUrl);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> unlink(HttpServletRequest request) {
        kakaoLinkService.unlink(authenticatedUser(request));
        return ResponseEntity.noContent().build();
    }

    private AuthenticatedUser authenticatedUser(HttpServletRequest request) {
        Object value = request.getAttribute(SupabaseAuthenticationFilter.AUTHENTICATED_USER_ATTRIBUTE);
        if (value instanceof AuthenticatedUser user) {
            return user;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user is not resolved.");
    }
}
