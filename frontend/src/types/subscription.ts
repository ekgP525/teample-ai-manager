import type { PlanType } from "./transcription";

export type SubscriptionStatus = "ACTIVE" | "CANCELED" | "PAST_DUE";

export interface Subscription {
  id: string;
  status: SubscriptionStatus;
  plan: PlanType;
  amount: number;
  cardCompany: string | null;
  cardNumber: string | null;
  currentPeriodStart: string;
  currentPeriodEnd: string;
  nextBillingAt: string | null;
  canceledAt: string | null;
  failedAttempts: number;
  lastError: string | null;
  premiumActive: boolean;
}

export interface SubscriptionCheckout {
  clientKey: string;
  customerKey: string;
  amount: number;
  orderName: string;
  customerEmail: string | null;
  customerName: string | null;
  testMode: boolean;
}

export interface PaymentRecord {
  id: string;
  orderId: string;
  orderName: string | null;
  amount: number;
  status: "DONE" | "FAILED";
  failureMessage: string | null;
  approvedAt: string | null;
  createdAt: string;
}

export interface KakaoLinkStatus {
  linked: boolean;
  configured: boolean;
  deadlineReminders: boolean;
  needsReconnect: boolean;
  linkedAt: string | null;
  lastNotifiedAt: string | null;
}

export interface KakaoConnectUrl {
  url: string;
  state: string;
}
