import type {
  PaymentRecord,
  SubscriptionCheckout,
  Subscription,
} from "@/types/subscription";
import { apiRequest } from "./client";

export function getMySubscription(signal?: AbortSignal) {
  return apiRequest<Subscription | undefined>("/api/me/subscription", {
    errorMessage: "구독 정보를 불러오지 못했습니다.",
    signal,
  });
}

export function startSubscriptionCheckout() {
  return apiRequest<SubscriptionCheckout>("/api/me/subscription/checkout", {
    method: "POST",
    errorMessage: "결제를 시작하지 못했습니다.",
  });
}

export function confirmSubscription(authKey: string, customerKey: string) {
  return apiRequest<Subscription>("/api/me/subscription/confirm", {
    method: "POST",
    body: JSON.stringify({ authKey, customerKey }),
    errorMessage: "구독을 완료하지 못했습니다.",
  });
}

export function cancelSubscription() {
  return apiRequest<Subscription>("/api/me/subscription/cancel", {
    method: "POST",
    errorMessage: "구독을 해지하지 못했습니다.",
  });
}

export function getMyPayments(signal?: AbortSignal) {
  return apiRequest<PaymentRecord[]>("/api/me/subscription/payments", {
    errorMessage: "결제 내역을 불러오지 못했습니다.",
    signal,
  });
}
