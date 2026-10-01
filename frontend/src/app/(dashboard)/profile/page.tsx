"use client";

import { useSearchParams } from "next/navigation";
import { Suspense, useCallback, useEffect, useState } from "react";
import { KakaoLinkCard } from "@/components/kakao-link-card";
import { SignOutButton } from "@/components/sign-out-button";
import { SubscriptionCard } from "@/components/subscription-card";
import { hasAdminTestSession } from "@/lib/admin-test-auth";
import { getCurrentAuthUser } from "@/lib/api/auth";
import { getMyPlan } from "@/lib/api/plan";
import { supabase } from "@/lib/supabase";
import type { UserPlan } from "@/types/transcription";

interface ProfileData {
  name: string;
  email: string;
  provider: string;
  userId: string;
}

export default function ProfilePage() {
  return (
    <Suspense
      fallback={
        <main className="flex flex-1 items-center justify-center px-4 py-12 text-sm text-zinc-500">
          프로필을 불러오는 중...
        </main>
      }
    >
      <ProfileContent />
    </Suspense>
  );
}

function ProfileContent() {
  const searchParams = useSearchParams();
  const kakaoLinkedNotice =
    searchParams.get("kakao") === "linked" ? "카카오 연결을 완료했습니다." : "";
  const [profile, setProfile] = useState<ProfileData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [plan, setPlan] = useState<UserPlan | null>(null);
  const [planError, setPlanError] = useState("");
  const [copied, setCopied] = useState(false);

  const loadPlan = useCallback((signal?: AbortSignal) => {
    return getMyPlan(signal)
      .then((data) => {
        setPlan(data);
        setPlanError("");
      })
      .catch((planLoadError: unknown) => {
        if (signal?.aborted) return;
        setPlanError(
          planLoadError instanceof Error
            ? planLoadError.message
            : "요금제 정보를 불러오지 못했습니다."
        );
      });
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void loadPlan(controller.signal);
    return () => controller.abort();
  }, [loadPlan]);

  useEffect(() => {
    let isMounted = true;
    const controller = new AbortController();

    async function loadProfile(): Promise<ProfileData> {
      // 관리자 테스트 세션은 Supabase 사용자가 없으므로 백엔드 /api/auth/me 정보를 쓴다.
      if (hasAdminTestSession()) {
        const user = await getCurrentAuthUser(controller.signal);
        return {
          name: user.memberKey,
          email: user.email ?? "관리자 테스트 계정",
          provider: "관리자 테스트",
          userId: user.authUserId,
        };
      }

      const { data, error: userError } = await supabase.auth.getUser();
      if (userError) throw userError;
      if (!data.user) throw new Error("로그인 사용자 정보가 없습니다.");

      const metadata = data.user.user_metadata;
      return {
        name:
          metadata.name ||
          metadata.full_name ||
          data.user.email?.split("@")[0] ||
          "사용자",
        email: data.user.email || "이메일 정보 없음",
        provider: formatProvider(data.user.app_metadata.provider),
        userId: data.user.id,
      };
    }

    void loadProfile()
      .then((data) => {
        if (isMounted) setProfile(data);
      })
      .catch(() => {
        if (isMounted && !controller.signal.aborted) {
          setError("프로필 정보를 불러오지 못했습니다.");
        }
      })
      .finally(() => {
        if (isMounted) setIsLoading(false);
      });

    return () => {
      isMounted = false;
      controller.abort();
    };
  }, []);

  return (
    <main className="flex flex-1 flex-col items-center px-4 py-8 sm:py-12">
      <div className="w-full max-w-2xl">
        <div className="mb-8">
          <h1 className="text-2xl font-bold">프로필</h1>
          <p className="mt-1 text-sm text-zinc-500">
            현재 로그인한 계정 정보를 확인합니다.
          </p>
        </div>

        {isLoading ? (
          <div
            aria-live="polite"
            className="rounded-lg border border-zinc-200 p-8 text-center dark:border-zinc-700"
          >
            <p className="text-zinc-500">프로필을 불러오는 중...</p>
          </div>
        ) : error || !profile ? (
          <div
            role="alert"
            className="rounded-lg border border-red-200 bg-red-50 p-8 text-center dark:border-red-900 dark:bg-red-950"
          >
            <p className="text-sm text-red-600 dark:text-red-400">
              {error || "프로필 정보가 없습니다."}
            </p>
          </div>
        ) : (
          <section className="overflow-hidden rounded-lg border border-zinc-200 dark:border-zinc-700">
            <div className="flex items-center gap-4 border-b border-zinc-200 p-5 dark:border-zinc-700">
              <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-zinc-100 text-lg font-semibold text-zinc-700 dark:bg-zinc-800 dark:text-zinc-200">
                {profile.name.slice(0, 1).toUpperCase()}
              </div>
              <div className="min-w-0">
                <h2 className="truncate font-semibold">{profile.name}</h2>
                <p className="truncate text-sm text-zinc-500">
                  {profile.email}
                </p>
              </div>
            </div>
            <dl className="divide-y divide-zinc-100 px-5 dark:divide-zinc-800">
              <ProfileRow label="이름" value={profile.name} />
              <ProfileRow label="이메일" value={profile.email} />
              <ProfileRow label="로그인 방식" value={profile.provider} />
              <div className="grid gap-1 py-4 sm:grid-cols-[8rem_1fr] sm:gap-4">
                <dt className="text-sm text-zinc-500">계정 ID</dt>
                <dd className="flex min-w-0 flex-wrap items-center gap-2 text-sm">
                  <code className="min-w-0 break-all rounded bg-zinc-100 px-1.5 py-0.5 text-xs dark:bg-zinc-800">
                    {profile.userId}
                  </code>
                  <button
                    type="button"
                    onClick={() => {
                      void navigator.clipboard
                        .writeText(profile.userId)
                        .then(() => {
                          setCopied(true);
                          window.setTimeout(() => setCopied(false), 1500);
                        })
                        .catch(() => setCopied(false));
                    }}
                    className="rounded border border-zinc-300 px-2 py-0.5 text-xs transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
                  >
                    {copied ? "복사됨" : "복사"}
                  </button>
                </dd>
              </div>
            </dl>
            <div className="border-t border-zinc-200 p-5 dark:border-zinc-700">
              <SignOutButton className="rounded-lg border border-zinc-300 px-4 py-2 text-sm transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800" />
            </div>
          </section>
        )}

        {!isLoading && profile && (
          <section className="mt-6 rounded-lg border border-zinc-200 p-5 dark:border-zinc-700">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="font-semibold">요금제</h2>
              {plan && (
                <span
                  className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
                    plan.premium
                      ? "bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200"
                      : "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300"
                  }`}
                >
                  {plan.premium ? "프리미엄" : "무료"}
                </span>
              )}
            </div>
            {planError ? (
              <p className="mt-2 text-sm text-red-600 dark:text-red-400">{planError}</p>
            ) : !plan ? (
              <p className="mt-2 text-sm text-zinc-500">요금제를 확인하는 중...</p>
            ) : plan.premium ? (
              <div className="mt-2 space-y-1 text-sm text-zinc-600 dark:text-zinc-300">
                <p>음성·영상 회의록을 사용할 수 있습니다.</p>
                <p>
                  이번 달 전사 사용량 {plan.usage.monthMinutesUsed}분 / {plan.usage.monthMinutesLimit}분
                </p>
                {plan.expiresAt && <p>만료일 {formatDateTime(plan.expiresAt)}</p>}
              </div>
            ) : (
              <p className="mt-2 text-sm leading-6 text-zinc-600 dark:text-zinc-300">
                음성·영상 회의록과 카카오톡 마감 알림은 프리미엄 요금제에서 사용할 수 있습니다.
                아래에서 구독을 시작하거나, 위의 계정 ID를 운영진에게 전달해 열어 달라고 요청할 수 있습니다.
              </p>
            )}
          </section>
        )}

        {!isLoading && profile && (
          <>
            <SubscriptionCard plan={plan} onPlanChanged={() => void loadPlan()} />
            <KakaoLinkCard initialNotice={kakaoLinkedNotice} />
          </>
        )}
      </div>
    </main>
  );
}

function ProfileRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid gap-1 py-4 sm:grid-cols-[8rem_1fr] sm:gap-4">
      <dt className="text-sm text-zinc-500">{label}</dt>
      <dd className="break-words text-sm font-medium">{value}</dd>
    </div>
  );
}

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "long",
    day: "numeric",
  }).format(date);
}

function formatProvider(provider?: string) {
  if (provider === "google") return "Google";
  if (provider === "kakao") return "카카오";
  if (provider === "email") return "이메일";
  return provider || "알 수 없음";
}
