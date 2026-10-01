package com.teample.controller;

import com.teample.dto.auth.AdminLoginRequest;
import com.teample.dto.auth.AdminLoginResponse;
import com.teample.dto.auth.AuthUserResponse;
import com.teample.security.AdminTestAuthService;
import com.teample.security.AuthenticatedUser;
import com.teample.security.SupabaseAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private static final String ADMIN_AUTH_MODE = "ADMIN_TEST";
    private static final String SUPABASE_AUTH_MODE = "SUPABASE";

    private final AdminTestAuthService adminTestAuthService;

    /**
     * 관리자 테스트 로그인 확인. ADMIN_TEST_ENABLED가 아니면 404로 존재 자체를 숨기고,
     * 같은 클라이언트가 10분 안에 5번 실패하면 429로 막는다.
     */
    @PostMapping("/admin/verify")
    public ResponseEntity<AdminLoginResponse> verifyAdmin(
            @RequestBody(required = false) AdminLoginRequest request, HttpServletRequest servletRequest
    ) {
        if (!adminTestAuthService.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String clientKey = servletRequest.getRemoteAddr();
        if (adminTestAuthService.isRateLimited(clientKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "관리자 로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        }
        if (request == null || !adminTestAuthService.matches(request.id(), request.password())) {
            adminTestAuthService.recordFailure(clientKey);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin credentials.");
        }
        adminTestAuthService.clearFailures(clientKey);
        return ResponseEntity.ok(new AdminLoginResponse(true, adminTestAuthService.adminId(), ADMIN_AUTH_MODE));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUserResponse> me(HttpServletRequest request) {
        Object value = request.getAttribute(SupabaseAuthenticationFilter.AUTHENTICATED_USER_ATTRIBUTE);
        if (!(value instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user is not resolved.");
        }

        boolean admin = Boolean.TRUE.equals(request.getAttribute(SupabaseAuthenticationFilter.ADMIN_TEST_USER_ATTRIBUTE));
        return ResponseEntity.ok(new AuthUserResponse(
                user.authUserId(),
                user.memberKey(),
                user.email(),
                admin,
                admin ? ADMIN_AUTH_MODE : SUPABASE_AUTH_MODE
        ));
    }
}
