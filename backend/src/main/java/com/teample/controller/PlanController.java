package com.teample.controller;

import com.teample.dto.plan.PlanGrantRequest;
import com.teample.dto.plan.PlanResponse;
import com.teample.dto.plan.UserPlanResponse;
import com.teample.security.AuthenticatedUser;
import com.teample.security.SupabaseAuthenticationFilter;
import com.teample.service.PlanService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    /** 현재 사용자의 요금제와 프리미엄 사용량. */
    @GetMapping("/api/me/plan")
    public ResponseEntity<PlanResponse> myPlan(HttpServletRequest request) {
        return ResponseEntity.ok(PlanResponse.from(planService.resolve(authenticatedUser(request), isAdmin(request))));
    }

    /** 관리자가 특정 사용자에게 요금제를 수동 부여한다. 결제 연동 전 임시 경로. */
    @PutMapping("/api/admin/users/{userId}/plan")
    public ResponseEntity<UserPlanResponse> grantPlan(
            @PathVariable String userId,
            @Valid @RequestBody PlanGrantRequest body,
            HttpServletRequest request
    ) {
        AuthenticatedUser user = authenticatedUser(request);
        ensurePlanAdmin(user, isAdmin(request));
        return ResponseEntity.ok(UserPlanResponse.from(
                planService.grant(userId, body.plan(), body.expiresAt(), body.note(), user.authUserId())));
    }

    @GetMapping("/api/admin/users/plans")
    public ResponseEntity<List<UserPlanResponse>> listPlans(HttpServletRequest request) {
        ensurePlanAdmin(authenticatedUser(request), isAdmin(request));
        return ResponseEntity.ok(planService.findAll().stream().map(UserPlanResponse::from).toList());
    }

    private void ensurePlanAdmin(AuthenticatedUser user, boolean admin) {
        if (!planService.isPlanAdmin(user, admin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "요금제를 관리할 권한이 없습니다.");
        }
    }

    private AuthenticatedUser authenticatedUser(HttpServletRequest request) {
        Object value = request.getAttribute(SupabaseAuthenticationFilter.AUTHENTICATED_USER_ATTRIBUTE);
        if (value instanceof AuthenticatedUser user) {
            return user;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user is not resolved.");
    }

    private boolean isAdmin(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(SupabaseAuthenticationFilter.ADMIN_TEST_USER_ATTRIBUTE));
    }
}
