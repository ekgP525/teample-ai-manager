package com.teample.service;

import com.teample.entity.PlanType;
import com.teample.entity.UserPlan;
import com.teample.repository.TranscriptionRepository;
import com.teample.repository.UserPlanRepository;
import com.teample.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanServiceTest {

    private final AuthenticatedUser user = new AuthenticatedUser("user-1", "박규남", "gyunam@example.com");

    @Test
    void userWithoutPlanRowIsFree() {
        UserPlanRepository plans = mock(UserPlanRepository.class);
        TranscriptionRepository transcriptions = mock(TranscriptionRepository.class);
        when(plans.findById("user-1")).thenReturn(Optional.empty());
        PlanService service = new PlanService(plans, transcriptions, 600, "");

        PlanService.PlanSnapshot snapshot = service.resolve(user, false);

        assertThat(snapshot.plan()).isEqualTo(PlanType.FREE);
        assertThat(snapshot.premium()).isFalse();
        assertThatThrownBy(() -> service.ensureTranscriptionAllowed(user, false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void expiredPremiumFallsBackToFree() {
        UserPlanRepository plans = mock(UserPlanRepository.class);
        TranscriptionRepository transcriptions = mock(TranscriptionRepository.class);
        when(plans.findById("user-1")).thenReturn(Optional.of(UserPlan.builder()
                .userId("user-1").plan(PlanType.PREMIUM)
                .expiresAt(LocalDateTime.now().minusDays(1)).build()));
        PlanService service = new PlanService(plans, transcriptions, 600, "");

        assertThat(service.resolve(user, false).premium()).isFalse();
    }

    @Test
    void activePremiumUnderQuotaIsAllowed() {
        UserPlanRepository plans = mock(UserPlanRepository.class);
        TranscriptionRepository transcriptions = mock(TranscriptionRepository.class);
        when(plans.findById("user-1")).thenReturn(Optional.of(UserPlan.builder()
                .userId("user-1").plan(PlanType.PREMIUM).build()));
        when(transcriptions.countByCreatedByAndStatusIn(eq("user-1"), any())).thenReturn(0L);
        when(transcriptions.sumDurationMsByCreatedBySince(eq("user-1"), any())).thenReturn(10L * 60_000);
        PlanService service = new PlanService(plans, transcriptions, 600, "");

        PlanService.PlanSnapshot snapshot = service.resolve(user, false);

        assertThat(snapshot.premium()).isTrue();
        assertThat(snapshot.usage().monthMinutesUsed()).isEqualTo(10);
        assertThatCode(() -> service.ensureTranscriptionAllowed(user, false)).doesNotThrowAnyException();
    }

    @Test
    void premiumOverMonthlyQuotaIsRejectedWith429() {
        UserPlanRepository plans = mock(UserPlanRepository.class);
        TranscriptionRepository transcriptions = mock(TranscriptionRepository.class);
        when(plans.findById("user-1")).thenReturn(Optional.of(UserPlan.builder()
                .userId("user-1").plan(PlanType.PREMIUM).build()));
        when(transcriptions.countByCreatedByAndStatusIn(eq("user-1"), any())).thenReturn(0L);
        when(transcriptions.sumDurationMsByCreatedBySince(eq("user-1"), any())).thenReturn(600L * 60_000);
        PlanService service = new PlanService(plans, transcriptions, 600, "");

        assertThatThrownBy(() -> service.ensureTranscriptionAllowed(user, false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429");
    }

    @Test
    void inFlightTranscriptionBlocksAnotherUpload() {
        UserPlanRepository plans = mock(UserPlanRepository.class);
        TranscriptionRepository transcriptions = mock(TranscriptionRepository.class);
        when(plans.findById("user-1")).thenReturn(Optional.of(UserPlan.builder()
                .userId("user-1").plan(PlanType.PREMIUM).build()));
        when(transcriptions.countByCreatedByAndStatusIn(eq("user-1"), any())).thenReturn(1L);
        when(transcriptions.sumDurationMsByCreatedBySince(eq("user-1"), any())).thenReturn(0L);
        PlanService service = new PlanService(plans, transcriptions, 600, "");

        assertThatThrownBy(() -> service.ensureTranscriptionAllowed(user, false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void adminTestUserIsAlwaysPremiumAndPlanAdmin() {
        UserPlanRepository plans = mock(UserPlanRepository.class);
        TranscriptionRepository transcriptions = mock(TranscriptionRepository.class);
        when(transcriptions.countByCreatedByAndStatusIn(anyString(), any())).thenReturn(0L);
        when(transcriptions.sumDurationMsByCreatedBySince(anyString(), any())).thenReturn(0L);
        PlanService service = new PlanService(plans, transcriptions, 600, "");

        assertThat(service.resolve(user, true).premium()).isTrue();
        assertThat(service.isPlanAdmin(user, true)).isTrue();
        assertThat(service.isPlanAdmin(user, false)).isFalse();
    }

    @Test
    void configuredAdminIdsCanManagePlans() {
        PlanService service = new PlanService(mock(UserPlanRepository.class), mock(TranscriptionRepository.class),
                600, " user-1 , other ");

        assertThat(service.isPlanAdmin(user, false)).isTrue();
        assertThat(service.isPlanAdmin(new AuthenticatedUser("user-9", "x", null), false)).isFalse();
    }

    @Test
    void grantAtLeastKeepsLaterAdminExpiryAndExtendsShorterOne() {
        UserPlanRepository plans = mock(UserPlanRepository.class);
        when(plans.save(any(UserPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlanService service = new PlanService(plans, mock(TranscriptionRepository.class), 600, "");
        LocalDateTime adminExpiry = LocalDateTime.now().plusDays(365);
        LocalDateTime subscriptionExpiry = LocalDateTime.now().plusDays(33);
        when(plans.findById("target")).thenReturn(Optional.of(UserPlan.builder()
                .userId("target").plan(PlanType.PREMIUM).expiresAt(adminExpiry).grantedBy("admin-1").note("베타").build()));

        UserPlan kept = service.grantAtLeast("target", PlanType.PREMIUM, subscriptionExpiry, "구독 결제", "subscription");

        assertThat(kept.getExpiresAt()).isEqualTo(adminExpiry);
        assertThat(kept.getGrantedBy()).isEqualTo("admin-1");
        verify(plans, never()).save(any());

        when(plans.findById("target")).thenReturn(Optional.of(UserPlan.builder()
                .userId("target").plan(PlanType.PREMIUM).expiresAt(LocalDateTime.now().plusDays(1)).grantedBy("subscription").build()));
        UserPlan extended = service.grantAtLeast("target", PlanType.PREMIUM, subscriptionExpiry, "구독 결제", "subscription");
        assertThat(extended.getExpiresAt()).isEqualTo(subscriptionExpiry);
        assertThat(extended.getGrantedBy()).isEqualTo("subscription");

        when(plans.findById("target")).thenReturn(Optional.of(UserPlan.builder()
                .userId("target").plan(PlanType.PREMIUM).expiresAt(null).grantedBy("admin-1").build()));
        UserPlan unlimited = service.grantAtLeast("target", PlanType.PREMIUM, subscriptionExpiry, "구독 결제", "subscription");
        assertThat(unlimited.getExpiresAt()).isNull();
    }

    @Test
    void shortenSubscriptionGrantOnlyTouchesRowsGrantedBySubscription() {
        UserPlanRepository plans = mock(UserPlanRepository.class);
        when(plans.save(any(UserPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlanService service = new PlanService(plans, mock(TranscriptionRepository.class), 600, "");
        LocalDateTime periodEnd = LocalDateTime.now().plusDays(10);
        when(plans.findById("admin-granted")).thenReturn(Optional.of(UserPlan.builder()
                .userId("admin-granted").plan(PlanType.PREMIUM).expiresAt(null).grantedBy("admin-1").build()));
        when(plans.findById("sub-granted")).thenReturn(Optional.of(UserPlan.builder()
                .userId("sub-granted").plan(PlanType.PREMIUM).expiresAt(periodEnd.plusDays(3)).grantedBy("subscription").build()));

        assertThat(service.shortenSubscriptionGrant("admin-granted", periodEnd, "해지")).isEmpty();
        verify(plans, never()).save(any());

        UserPlan shortened = service.shortenSubscriptionGrant("sub-granted", periodEnd, "해지").orElseThrow();
        assertThat(shortened.getExpiresAt()).isEqualTo(periodEnd);
        verify(plans).save(shortened);
    }

    @Test
    void grantUpsertsPlanRow() {
        UserPlanRepository plans = mock(UserPlanRepository.class);
        when(plans.findById("target")).thenReturn(Optional.empty());
        when(plans.save(any(UserPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlanService service = new PlanService(plans, mock(TranscriptionRepository.class), 600, "");

        UserPlan saved = service.grant("target", PlanType.PREMIUM, null, "베타 테스터", "admin-1");

        assertThat(saved.getUserId()).isEqualTo("target");
        assertThat(saved.getPlan()).isEqualTo(PlanType.PREMIUM);
        assertThat(saved.getGrantedBy()).isEqualTo("admin-1");
        assertThat(saved.getNote()).isEqualTo("베타 테스터");
    }
}
