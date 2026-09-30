"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import { linkKakao } from "@/lib/api/kakao";

export default function KakaoCallbackPage() {
  return (
    <Suspense fallback={<Screen title="카카오 연결을 확인하는 중..." />}>
      <KakaoCallbackContent />
    </Suspense>
  );
}

function KakaoCallbackContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const code = searchParams.get("code");
  const state = searchParams.get("state");
  const kakaoError = searchParams.get("error");
  const kakaoErrorDescription = searchParams.get("error_description");
  const [linkError, setLinkError] = useState("");
  const startedRef = useRef(false);
  const paramError = kakaoError
    ? kakaoError === "access_denied"
      ? "카카오 동의를 취소했습니다. 알림을 받으려면 다시 연결해 주세요."
      : kakaoErrorDescription || "카카오에서 오류를 돌려주었습니다."
    : !code
      ? "연결 정보가 없습니다. 프로필에서 다시 시도해 주세요."
      : "";
  const error = paramError || linkError;

  useEffect(() => {
    if (startedRef.current || kakaoError || !code) return;
    startedRef.current = true;

    void linkKakao(code, `${window.location.origin}/kakao/callback`, state)
      .then(() => router.replace("/profile?kakao=linked"))
      .catch((requestError: unknown) => {
        setLinkError(requestError instanceof Error ? requestError.message : "카카오를 연결하지 못했습니다.");
      });
  }, [code, state, kakaoError, router]);

  if (error) {
    return <Screen title="카카오 연결에 실패했습니다." description={error} tone="error" />;
  }
  return <Screen title="카카오 계정을 연결하는 중..." description="잠시만 기다려 주세요." />;
}

function Screen({
  title,
  description,
  tone = "neutral",
}: {
  title: string;
  description?: string;
  tone?: "neutral" | "error";
}) {
  return (
    <main className="flex flex-1 items-center justify-center px-4 py-12">
      <div
        aria-live="polite"
        className={`w-full max-w-lg rounded-lg border p-6 text-center ${
          tone === "error"
            ? "border-red-200 bg-red-50 dark:border-red-900 dark:bg-red-950"
            : "border-zinc-200 dark:border-zinc-700"
        }`}
      >
        <h1 className="text-lg font-semibold">{title}</h1>
        {description && <p className="mt-2 text-sm leading-6 text-zinc-600 dark:text-zinc-300">{description}</p>}
        {tone === "error" && (
          <Link
            href="/profile"
            className="mt-5 inline-block rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
          >
            프로필로 돌아가기
          </Link>
        )}
      </div>
    </main>
  );
}
