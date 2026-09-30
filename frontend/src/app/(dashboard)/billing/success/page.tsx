"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import { confirmSubscription } from "@/lib/api/subscription";
import type { Subscription } from "@/types/subscription";

export default function BillingSuccessPage() {
  return (
    <Suspense fallback={<StatusScreen title="구독을 확인하는 중..." />}>
      <BillingSuccessContent />
    </Suspense>
  );
}

function BillingSuccessContent() {
  const searchParams = useSearchParams();
  const authKey = searchParams.get("authKey");
  const customerKey = searchParams.get("customerKey");
  const hasParams = Boolean(authKey && customerKey);
  const [subscription, setSubscription] = useState<Subscription | null>(null);
  const [requestError, setRequestError] = useState("");
  const [isRequestDone, setIsRequestDone] = useState(false);
  const startedRef = useRef(false);
  const error = hasParams ? requestError : "결제 정보가 없습니다. 프로필에서 다시 시도해 주세요.";
  const isDone = hasParams ? isRequestDone : true;

  useEffect(() => {
    if (startedRef.current || !authKey || !customerKey) return;
    startedRef.current = true;

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

  if (!isDone) {
    return <StatusScreen title="카드를 등록하고 첫 결제를 진행하는 중..." description="잠시만 기다려 주세요. 창을 닫지 마세요." />;
  }

  if (error || !subscription) {
    return (
      <StatusScreen
        title="구독을 완료하지 못했습니다."
        description={error}
        tone="error"
        action={{ href: "/profile", label: "프로필로 돌아가기" }}
      />
    );
  }

  return (
    <StatusScreen
      title="프리미엄 구독이 시작되었습니다."
      description={`${subscription.cardCompany ?? "카드"} ${subscription.cardNumber ?? ""}로 월 ${subscription.amount.toLocaleString()}원이 결제되며, 다음 결제일은 ${formatDate(subscription.nextBillingAt)}입니다.`}
      tone="success"
      action={{ href: "/profile", label: "프로필에서 확인" }}
    />
  );
}

function StatusScreen({
  title,
  description,
  tone = "neutral",
  action,
}: {
  title: string;
  description?: string;
  tone?: "neutral" | "success" | "error";
  action?: { href: string; label: string };
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
        <h1 className="text-lg font-semibold">{title}</h1>
        {description && <p className="mt-2 text-sm leading-6 text-zinc-600 dark:text-zinc-300">{description}</p>}
        {action && (
          <Link
            href={action.href}
            className="mt-5 inline-block rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
          >
            {action.label}
          </Link>
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
