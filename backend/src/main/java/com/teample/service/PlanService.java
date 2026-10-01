package com.teample.service;

import com.teample.entity.PlanType;
import com.teample.entity.TranscriptionStatus;
import com.teample.entity.UserPlan;
import com.teample.repository.TranscriptionRepository;
import com.teample.repository.UserPlanRepository;
import com.teample.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 요금제 조회·부여와 프리미엄 기능 사용 가능 여부 판정.
 * 결제 연동 전이므로 부여는 관리자 계정(app.plan.admins 또는 관리자 테스트 인증)이 수동으로 한다.
 */
@Service
public class PlanService {

    public static final String SUBSCRIPTION_GRANTER = "subscription";

    private static final Set<TranscriptionStatus> IN_FLIGHT =
            Set.of(TranscriptionStatus.QUEUED, TranscriptionStatus.PROCESSING);

    private final UserPlanRepository userPlanRepository;
    private final TranscriptionRepository transcriptionRepository;
    private final long premiumMonthlyMinutes;
    private final Set<String> planAdminIds;

    public PlanService(
            UserPlanRepository userPlanRepository,
            TranscriptionRepository transcriptionRepository,
            @Value("${app.plan.premium-monthly-minutes:600}") long premiumMonthlyMinutes,
            @Value("${app.plan.admins:}") String planAdmins
    ) {
        this.userPlanRepository = userPlanRepository;
        this.transcriptionRepository = transcriptionRepository;
        this.premiumMonthlyMinutes = premiumMonthlyMinutes;
        this.planAdminIds = Arrays.stream(planAdmins == null ? new String[0] : planAdmins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public PlanSnapshot resolve(AuthenticatedUser user, boolean admin) {
        LocalDateTime now = LocalDateTime.now();
        if (admin) {
            return new PlanSnapshot(PlanType.PREMIUM, null, true, usageOf(user, now));
        }
        if (user == null) {
            return new PlanSnapshot(PlanType.FREE, null, false, PlanUsage.empty(premiumMonthlyMinutes));
        }
        Optional<UserPlan> stored = userPlanRepository.findById(user.authUserId());
        boolean premium = stored
                .filter(plan -> plan.getPlan() == PlanType.PREMIUM)
                .filter(plan -> plan.isActiveAt(now))
                .isPresent();
        LocalDateTime expiresAt = stored.map(UserPlan::getExpiresAt).orElse(null);
        return new PlanSnapshot(premium ? PlanType.PREMIUM : PlanType.FREE, expiresAt, premium, usageOf(user, now));
    }

    @Transactional(readOnly = true)
    public boolean isPlanAdmin(AuthenticatedUser user, boolean admin) {
        if (admin) {
            return true;
        }
        return user != null && planAdminIds.contains(user.authUserId());
    }

    /**
     * 음성·영상 전사를 시작할 수 있는지 확인한다.
     * 403: 프리미엄 아님, 429: 월 사용량 초과, 409: 진행 중인 전사가 이미 있음.
     */
    @Transactional(readOnly = true)
    public void ensureTranscriptionAllowed(AuthenticatedUser user, boolean admin) {
        PlanSnapshot snapshot = resolve(user, admin);
        if (!snapshot.premium()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "음성·영상 회의록은 프리미엄 요금제에서 사용할 수 있습니다.");
        }
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user is not resolved.");
        }
        if (transcriptionRepository.countByCreatedByAndStatusIn(user.authUserId(), IN_FLIGHT) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "이미 처리 중인 전사가 있습니다. 완료된 뒤 다시 시도해 주세요.");
        }
        if (!admin && snapshot.usage().monthMinutesUsed() >= snapshot.usage().monthMinutesLimit()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "이번 달 전사 사용량(" + snapshot.usage().monthMinutesLimit() + "분)을 모두 사용했습니다.");
        }
    }

    @Transactional
    public UserPlan grant(String targetUserId, PlanType plan, LocalDateTime expiresAt, String note, String grantedBy) {
        if (targetUserId == null || targetUserId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "대상 사용자 ID가 비어 있습니다.");
        }
        if (plan == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요금제 값이 비어 있습니다.");
        }
        UserPlan userPlan = userPlanRepository.findById(targetUserId.trim())
                .orElseGet(() -> UserPlan.builder().userId(targetUserId.trim()).build());
        userPlan.setPlan(plan);
        userPlan.setExpiresAt(expiresAt);
        userPlan.setNote(note);
        userPlan.setGrantedBy(grantedBy);
        return userPlanRepository.save(userPlan);
    }

    /**
     * 구독 결제 성공 시 호출. 기존 만료일이 더 늦거나(관리자 수동 부여 등) 무기한이면 그대로 두고,
     * 그렇지 않을 때만 만료일을 늘린다. 절대 줄이지 않는다.
     */
    @Transactional
    public UserPlan grantAtLeast(String targetUserId, PlanType plan, LocalDateTime expiresAt, String note, String grantedBy) {
        if (targetUserId == null || targetUserId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "대상 사용자 ID가 비어 있습니다.");
        }
        if (plan == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요금제 값이 비어 있습니다.");
        }
        Optional<UserPlan> existing = userPlanRepository.findById(targetUserId.trim());
        if (existing.isPresent() && existing.get().getPlan() == plan) {
            LocalDateTime current = existing.get().getExpiresAt();
            boolean currentIsLaterOrUnlimited = current == null || (expiresAt != null && !current.isBefore(expiresAt));
            if (currentIsLaterOrUnlimited) {
                return existing.get();
            }
        }
        return grant(targetUserId, plan, expiresAt, note, grantedBy);
    }

    /**
     * 구독 해지 시 호출. 요금제 행이 구독 결제로 부여된 것(grantedBy == "subscription")일 때만 만료일을 당긴다.
     * 관리자가 수동으로 준 요금제는 건드리지 않는다.
     */
    @Transactional
    public Optional<UserPlan> shortenSubscriptionGrant(String targetUserId, LocalDateTime expiresAt, String note) {
        if (targetUserId == null || targetUserId.isBlank()) {
            return Optional.empty();
        }
        return userPlanRepository.findById(targetUserId.trim())
                .filter(userPlan -> SUBSCRIPTION_GRANTER.equals(userPlan.getGrantedBy()))
                .map(userPlan -> {
                    if (expiresAt != null && (userPlan.getExpiresAt() == null || userPlan.getExpiresAt().isAfter(expiresAt))) {
                        userPlan.setExpiresAt(expiresAt);
                        userPlan.setNote(note);
                        return userPlanRepository.save(userPlan);
                    }
                    return userPlan;
                });
    }

    @Transactional(readOnly = true)
    public List<UserPlan> findAll() {
        return userPlanRepository.findAll();
    }

    private PlanUsage usageOf(AuthenticatedUser user, LocalDateTime now) {
        if (user == null) {
            return PlanUsage.empty(premiumMonthlyMinutes);
        }
        LocalDateTime monthStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        long usedMs = transcriptionRepository.sumDurationMsByCreatedBySince(user.authUserId(), monthStart);
        long usedMinutes = (usedMs + 59_999) / 60_000;
        return new PlanUsage(usedMinutes, premiumMonthlyMinutes);
    }

    public record PlanSnapshot(PlanType plan, LocalDateTime expiresAt, boolean premium, PlanUsage usage) {
    }

    public record PlanUsage(long monthMinutesUsed, long monthMinutesLimit) {
        static PlanUsage empty(long limit) {
            return new PlanUsage(0, limit);
        }
    }
}
