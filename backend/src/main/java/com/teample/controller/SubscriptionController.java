package com.teample.controller;

import com.teample.dto.subscription.CheckoutResponse;
import com.teample.dto.subscription.ConfirmSubscriptionRequest;
import com.teample.dto.subscription.PaymentResponse;
import com.teample.dto.subscription.SubscriptionResponse;
import com.teample.security.AuthenticatedUser;
import com.teample.security.SupabaseAuthenticationFilter;
import com.teample.service.SubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/me/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    public ResponseEntity<SubscriptionResponse> get(HttpServletRequest request) {
        return subscriptionService.find(authenticatedUser(request))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(HttpServletRequest request) {
        return ResponseEntity.ok(subscriptionService.checkout(authenticatedUser(request)));
    }

    @PostMapping("/confirm")
    public ResponseEntity<SubscriptionResponse> confirm(
            @Valid @RequestBody ConfirmSubscriptionRequest body, HttpServletRequest request
    ) {
        return ResponseEntity.ok(subscriptionService.confirm(authenticatedUser(request), body.authKey(), body.customerKey()));
    }

    @PostMapping("/cancel")
    public ResponseEntity<SubscriptionResponse> cancel(HttpServletRequest request) {
        return ResponseEntity.ok(subscriptionService.cancel(authenticatedUser(request)));
    }

    @GetMapping("/payments")
    public ResponseEntity<List<PaymentResponse>> payments(HttpServletRequest request) {
        return ResponseEntity.ok(subscriptionService.payments(authenticatedUser(request)));
    }

    private AuthenticatedUser authenticatedUser(HttpServletRequest request) {
        Object value = request.getAttribute(SupabaseAuthenticationFilter.AUTHENTICATED_USER_ATTRIBUTE);
        if (value instanceof AuthenticatedUser user) {
            return user;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user is not resolved.");
    }
}
