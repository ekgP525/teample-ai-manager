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

    @PostMapping("/admin/verify")
    public ResponseEntity<AdminLoginResponse> verifyAdmin(@RequestBody AdminLoginRequest request) {
        if (request == null || !adminTestAuthService.matches(request.id(), request.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin credentials.");
        }
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