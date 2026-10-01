"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useCallback, useEffect, useRef, useState } from "react";
import {
  BILLING_TEST_MODE_MESSAGE,
  BILLING_TEST_MODE_STORAGE_KEY,
  type CheckoutIntent,
} from "@/components/subscription-card";
import { confirmSubscription } from "@/lib/api/subscription";
import type { Subscription } from "@/types/subscription";

export default function BillingSuccessPage() {
  return (
    <Suspense fallback={<StatusScreen title="구독을 확인하는 중..." />}>
      <BillingSuccessContent />
    </Suspense>
  );
}

function readTestModeFlag() {
  if (typeof window === "undefined") return false;
  try {
    return window.sessionStorage.getItem(BILLING_TEST_MODE_STORAGE_KEY) === "true";
  } catch {
    return false;
  }
}

function parseIntent(value: string | null): CheckoutIntent {
  return value === "change-card" || value === "resume" ? value : "start";
}

function BillingSuccessContent() {
  const searchParams = useSearchParams();
  const authKey = searchParams.get("authKey");
  const customerKey = searchParams.get("customerKey");
  const intent = parseIntent(searchParams.get("intent"));
  const hasParams = Boolean(authKey && customerKey);
  const [subscription, setSubscription] = useState<Subscription | null>(null);
  const [requestError, setRequestError] = useState("");
  const [isRequestDone, setIsRequestDone] = useState(false);
  // useSearchParams 때문에 이 트리는 클라이언트에서만 렌더링되므로 세션 스토리지를 바로 읽어도 된다.
  const [isTestMode] = useState(readTestModeFlag);
  const startedRef = useRef(false);
  const error = hasParams ? requestError : "결제 정보가 없습니다. 프로필에서 다시 시도해 주세요.";
  const isDone = hasParams ? isRequestDone : true;
  const testModeBanner = isTestMode ? BILLING_TEST_MODE_MESSAGE : undefined;

  const runConfirm = useCallback(() => {
    if (!authKey || !customerKey) return;

    void confirmSubscription(authKey, customerKey)
      .then((data) => {
        setSubscription(data);
        setRequestError("");
      })
      .catch((confirmError: unknown) => {
        setRequestError(
          confirmError instanceof Error ? confirmError.message : "구독을 완료하지 못했습니다."
        );
      })
      .finally(() => setIsRequestDone(true));
  }, [authKey, customerKey]);

  useEffect(() => {
    if (startedRef.current) return;
    startedRef.current = true;
    runConfirm();
  }, [runConfirm]);

  const handleRetry = () => {
    // 같은 authKey/customerKey로 다시 확인한다. 가드를 다시 세우고 상태를 초기화한 뒤 재요청한다.
    startedRef.current = true;
    setIsRequestDone(false);
    setRequestError("");
    runConfirm();
  };

  if (!isDone) {
    return (
      <StatusScreen
        title={
          intent === "change-card"
            ? "결제 카드를 변경하는 중..."
            : intent === "resume"
              ? "카드를 등록하고 구독을 다시 시작하는 중..."
              : "카드를 등록하고 첫 결제를 진행하는 중..."
        }
        description="잠시만 기다려 주세요. 창을 닫지 마세요."
        banner={testModeBanner}
      />
    );
  }

  if (error || !subscription) {
    return (
      <StatusScreen
        title={
          intent === "change-card"
            ? "결제 카드를 변경하지 못했습니다."
            : "구독을 완료하지 못했습니다."
        }
        description={error}
        tone="error"
        banner={testModeBanner}
        action={{ href: "/profile", label: "프로필로 돌아가기" }}
        secondaryAction={hasParams ? { label: "다시 시도", onClick: handleRetry } : undefined}
      />
    );
  }

  const card = `${subscription.cardCompany ?? "카드"} ${subscription.cardNumber ?? ""}`.trim();
  const nextBilling = formatDate(subscription.nextBillingAt);

  if (intent === "change-card") {
    return (
      <StatusScreen
        title="결제 카드를 변경했습니다."
        description={`앞으로 ${card}로 결제됩니다. 다음 결제일은 ${nextBilling}입니다.`}
        tone="success"
        banner={testModeBanner}
        action={{ href: "/profile", label: "프로필에서 확인" }}
      />
    );
  }

  return (
    <StatusScreen
      title={intent === "resume" ? "구독을 다시 시작했습니다." : "프리미엄 구독이 시작되었습니다."}
      description={`${card}로 월 ${subscription.amount.toLocaleString()}원이 결제되며, 다음 결제일은 ${nextBilling}입니다.`}
      tone="success"
      banner={testModeBanner}
      action={{ href: "/profile", label: "프로필에서 확인" }}
    />
  );
}

function StatusScreen({
  title,
  description,
  tone = "neutral",
  banner,
  action,
  secondaryAction,
}: {
  title: string;
  description?: string;
  tone?: "neutral" | "success" | "error";
  banner?: string;
  action?: { href: string; label: string };
  secondaryAction?: { label: string; onClick: () => void };
}) {
  const toneClass =
    tone === "success"
      ? "border-emerald-200 bg-emerald-50 dark:border-emerald-900 dark:bg-emerald-950"
      : tone === "error"
        ? "border-red-200 bg-red-50 dark:border-red-900 dark:bg-red-950"
        : "border-zinc-200 dark:border-zinc-700";

  return (
    <main className="flex flex-1 items-center justify-center px-4 py-12">
      <div className={`w-full max-w-lg rounded-lg border p-6 text-center ${toneClass}`} aria-live="polite">
        {banner && (
          <p
            role="status"
            className="mb-4 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:bg-amber-950 dark:text-amber-200"
          >
            {banner}
          </p>
        )}
        <h1 className="text-lg font-semibold">{title}</h1>
        {description && <p className="mt-2 text-sm leading-6 text-zinc-600 dark:text-zinc-300">{description}</p>}
        {(action || secondaryAction) && (
          <div className="mt-5 flex flex-wrap justify-center gap-2">
            {secondaryAction && (
              <button
                type="button"
                onClick={secondaryAction.onClick}
                className="rounded-lg border border-zinc-300 px-4 py-2 text-sm transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
              >
                {secondaryAction.label}
              </button>
            )}
            {action && (
              <Link
                href={action.href}
                className="inline-block rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
              >
                {action.label}
              </Link>
            )}
          </div>
        )}
      </div>
    </main>
  );
}

function formatDate(value: string | null) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "long", day: "numeric" }).format(date);
}
