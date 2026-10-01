"use client";

import { loadTossPayments } from "@tosspayments/tosspayments-sdk";
import { useEffect, useState } from "react";
import {
  cancelSubscription,
  getMyPayments,
  getMySubscription,
  startSubscriptionCheckout,
} from "@/lib/api/subscription";
import type { PaymentRecord, Subscription } from "@/types/subscription";
import type { UserPlan } from "@/types/transcription";

export type CheckoutIntent = "start" | "change-card" | "resume";

export const BILLING_TEST_MODE_STORAGE_KEY = "teample.billing.testMode";
export const BILLING_TEST_MODE_MESSAGE =
  "테스트 결제 환경입니다. 실제 출금은 발생하지 않습니다.";

export function SubscriptionCard({
  plan,
  onPlanChanged,
}: {
  plan: UserPlan | null;
  onPlanChanged: () => void;
}) {
  const [subscription, setSubscription] = useState<Subscription | null>(null);
  const [payments, setPayments] = useState<PaymentRecord[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [isStarting, setIsStarting] = useState(false);
  const [isTestMode, setIsTestMode] = useState(false);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [isCanceling, setIsCanceling] = useState(false);
  const [showPayments, setShowPayments] = useState(false);

  useEffect(() => {
    const controller = new AbortController();

    void Promise.all([
      getMySubscription(controller.signal),
      getMyPayments(controller.signal).catch(() => [] as PaymentRecord[]),
    ])
      .then(([subscriptionData, paymentData]) => {
        setSubscription(subscriptionData ?? null);
        setPayments(paymentData);
        setError("");
      })
      .catch((loadError: unknown) => {
        if (controller.signal.aborted) return;
        setError(
          loadError instanceof Error ? loadError.message : "구독 정보를 불러오지 못했습니다."
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });

    return () => controller.abort();
  }, []);

  const startCheckout = async (intent: CheckoutIntent) => {
    if (isStarting) return;
    setIsStarting(true);
    setError("");
    try {
      const checkout = await startSubscriptionCheckout();
      setIsTestMode(checkout.testMode);
      try {
        // 성공 페이지가 테스트 결제 안내를 이어서 보여줄 수 있게 남긴다.
        window.sessionStorage.setItem(
          BILLING_TEST_MODE_STORAGE_KEY,
          checkout.testMode ? "true" : "false"
        );
      } catch {
        // 스토리지를 쓸 수 없어도 결제는 계속 진행한다.
      }
      const successUrl = new URL("/billing/success", window.location.origin);
      successUrl.searchParams.set("intent", intent);
      const tossPayments = await loadTossPayments(checkout.clientKey);
      const payment = tossPayments.payment({ customerKey: checkout.customerKey });
      await payment.requestBillingAuth({
        method: "CARD",
        successUrl: successUrl.toString(),
        failUrl: `${window.location.origin}/billing/fail`,
        customerEmail: checkout.customerEmail ?? undefined,
        customerName: checkout.customerName ?? undefined,
      });
    } catch (checkoutError) {
      setError(getCheckoutErrorMessage(checkoutError));
      setIsStarting(false);
    }
  };

  const handleCancel = async () => {
    if (isCanceling) return;
    setIsCanceling(true);
    setError("");
    try {
      const updated = await cancelSubscription();
      setSubscription(updated);
      setConfirmCancel(false);
      onPlanChanged();
    } catch (cancelError) {
      setError(
        cancelError instanceof Error ? cancelError.message : "구독을 해지하지 못했습니다."
      );
    } finally {
      setIsCanceling(false);
    }
  };

  const isActive = subscription?.status === "ACTIVE";
  const isCanceled = subscription?.status === "CANCELED" && subscription.premiumActive;
  const isPastDue = subscription?.status === "PAST_DUE";

  return (
    <section className="mt-6 rounded-lg border border-zinc-200 p-5 dark:border-zinc-700">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="font-semibold">프리미엄 구독</h2>
        {subscription && (
          <span
            className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
              isActive
                ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-900 dark:text-emerald-200"
                : isPastDue
                  ? "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200"
                  : "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300"
            }`}
          >
            {isActive ? "구독 중" : isPastDue ? "결제 실패" : "해지됨"}
          </span>
        )}
      </div>

      {isLoading ? (
        <p className="mt-2 text-sm text-zinc-500">구독 정보를 확인하는 중...</p>
      ) : (
        <div className="mt-3 space-y-3 text-sm text-zinc-600 dark:text-zinc-300">
          {!subscription || (!isActive && !isCanceled && !isPastDue) ? (
            <>
              <p className="leading-6">
                월 4,900원으로 음성·영상 회의록, 카카오톡 마감 알림 등 프리미엄 기능을 사용할 수 있습니다.
                카드를 한 번 등록하면 30일마다 자동으로 결제되고 언제든 해지할 수 있습니다.
              </p>
              {plan?.premium && (
                <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:bg-amber-950 dark:text-amber-200">
                  지금은 운영진이 열어 준 프리미엄을 쓰고 있습니다. 구독을 시작하면 만료일이 결제 주기에 맞춰 갱신됩니다.
                </p>
              )}
              <button
                type="button"
                onClick={() => void startCheckout("start")}
                disabled={isStarting}
                aria-busy={isStarting}
                className="rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-800 disabled:cursor-wait disabled:opacity-50 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
              >
                {isStarting ? "결제창 여는 중..." : "카드 등록하고 구독 시작"}
              </button>
            </>
          ) : (
            <>
              <dl className="grid gap-2 sm:grid-cols-2">
                <Row label="결제 카드" value={`${subscription.cardCompany ?? "카드"} ${subscription.cardNumber ?? ""}`.trim()} />
                <Row label="금액" value={`월 ${subscription.amount.toLocaleString()}원`} />
                <Row label="이용 기간 종료" value={formatDate(subscription.currentPeriodEnd)} />
                <Row
                  label={isActive ? "다음 결제일" : "상태"}
                  value={
                    isActive
                      ? formatDate(subscription.nextBillingAt)
                      : isPastDue
                        ? "자동 결제가 계속 실패해 멈췄습니다"
                        : "해지 예약됨 (기간 종료 시 무료 전환)"
                  }
                />
              </dl>
              {isPastDue && subscription.lastError && (
                <p className="rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700 dark:bg-red-950 dark:text-red-300">
                  마지막 오류: {subscription.lastError}
                </p>
              )}
              <div className="flex flex-wrap gap-2">
                {(isPastDue || isCanceled) && (
                  <button
                    type="button"
                    onClick={() => void startCheckout("resume")}
                    disabled={isStarting}
                    className="rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-800 disabled:opacity-50 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
                  >
                    {isStarting ? "결제창 여는 중..." : isPastDue ? "카드 다시 등록" : "구독 다시 시작"}
                  </button>
                )}
                {isActive && (
                  <button
                    type="button"
                    onClick={() => void startCheckout("change-card")}
                    disabled={isStarting}
                    className="rounded-lg border border-zinc-300 px-4 py-2 text-sm transition-colors hover:bg-zinc-100 disabled:opacity-50 dark:border-zinc-700 dark:hover:bg-zinc-800"
                  >
                    {isStarting ? "결제창 여는 중..." : "카드 변경"}
                  </button>
                )}
                {isActive && !confirmCancel && (
                  <button
                    type="button"
                    onClick={() => setConfirmCancel(true)}
                    className="rounded-lg border border-red-300 px-4 py-2 text-sm text-red-600 transition-colors hover:bg-red-50 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-950"
                  >
                    구독 해지
                  </button>
                )}
              </div>
              {confirmCancel && (
                <div className="rounded-lg border border-red-200 bg-red-50 p-4 dark:border-red-900 dark:bg-red-950">
                  <p className="text-sm text-red-700 dark:text-red-300">
                    해지하면 {formatDate(subscription.currentPeriodEnd)}까지는 프리미엄이 유지되고 이후 결제되지 않습니다.
                  </p>
                  <div className="mt-3 flex gap-2">
                    <button
                      type="button"
                      onClick={() => void handleCancel()}
                      disabled={isCanceling}
                      className="rounded-lg bg-red-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
                    >
                      {isCanceling ? "처리 중..." : "해지 확인"}
                    </button>
                    <button
                      type="button"
                      onClick={() => setConfirmCancel(false)}
                      disabled={isCanceling}
                      className="rounded-lg border border-zinc-300 px-3 py-1.5 text-sm hover:bg-white disabled:opacity-50 dark:border-zinc-700 dark:hover:bg-zinc-900"
                    >
                      취소
                    </button>
                  </div>
                </div>
              )}
            </>
          )}

          {isTestMode && (
            <p
              role="status"
              className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:bg-amber-950 dark:text-amber-200"
            >
              {BILLING_TEST_MODE_MESSAGE}
            </p>
          )}

          {payments.length > 0 && (
            <div>
              <button
                type="button"
                onClick={() => setShowPayments((value) => !value)}
                className="text-xs text-blue-600 hover:underline dark:text-blue-400"
              >
                {showPayments ? "결제 내역 숨기기" : `결제 내역 보기 (${payments.length})`}
              </button>
              {showPayments && (
                <ul className="mt-2 divide-y divide-zinc-100 rounded-lg border border-zinc-200 text-xs dark:divide-zinc-800 dark:border-zinc-700">
                  {payments.map((payment) => (
                    <li key={payment.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2">
                      <span>{formatDateTime(payment.approvedAt ?? payment.createdAt)}</span>
                      <span>{payment.amount.toLocaleString()}원</span>
                      <span className={payment.status === "DONE" ? "text-emerald-600" : "text-red-600"}>
                        {payment.status === "DONE" ? "결제 완료" : `실패${payment.failureMessage ? ` · ${payment.failureMessage}` : ""}`}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}

          {error && (
            <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600 dark:bg-red-950 dark:text-red-400">
              {error}
            </p>
          )}
        </div>
      )}
    </section>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-zinc-500">{label}</dt>
      <dd className="font-medium text-zinc-800 dark:text-zinc-100">{value || "-"}</dd>
    </div>
  );
}

function getCheckoutErrorMessage(error: unknown) {
  if (error && typeof error === "object" && "code" in error) {
    const code = String((error as { code?: string }).code);
    if (code === "USER_CANCEL") return "카드 등록을 취소했습니다.";
  }
  return error instanceof Error ? error.message : "결제를 시작하지 못했습니다.";
}

function formatDate(value: string | null | undefined) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "long", day: "numeric" }).format(date);
}

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}
