"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, type FormEvent, useEffect, useState } from "react";
import { signInWithOAuth as signInWithSocialOAuth } from "@/lib/sign-in-with-oauth";
import { supabase } from "@/lib/supabase";

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const next = searchParams.get("next")?.startsWith("/")
    ? searchParams.get("next")!
    : "/projects";

  useEffect(() => {
    let isMounted = true;

    async function redirectIfSessionExists() {
      try {
        const {
          data: { session },
          error: sessionError,
        } = await supabase.auth.getSession();

        if (sessionError) throw sessionError;

        if (session && isMounted) {
          router.replace(next);
          router.refresh();
        }
      } catch {
        if (isMounted) {
          setError("인증 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
      }
    }

    void redirectIfSessionExists();

    return () => {
      isMounted = false;
    };
  }, [next, router]);

  async function signInWithEmail(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setIsLoading(true);

    try {
      const { error: signInError } = await supabase.auth.signInWithPassword({
        email,
        password,
      });

      if (signInError) {
        setError("이메일 또는 비밀번호를 확인해 주세요.");
        return;
      }

      router.replace(next);
      router.refresh();
    } catch {
      setError("인증 서버에 연결하지 못했습니다. 환경 설정과 네트워크를 확인해 주세요.");
    } finally {
      setIsLoading(false);
    }
  }

  async function signInWithOAuth(provider: "google" | "kakao") {
    setError(null);
    setIsLoading(true);

    try {
      const { error: signInError } = await signInWithSocialOAuth(provider, next);

      if (signInError) {
        setError(`${provider === "google" ? "Google" : "카카오"} 로그인에 실패했습니다. 다시 시도해 주세요.`);
        setIsLoading(false);
      }
    } catch {
      setError("인증 서버에 연결하지 못했습니다. 환경 설정과 네트워크를 확인해 주세요.");
      setIsLoading(false);
    }
  }

  return (
    <main className="flex flex-1 flex-col items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <h1 className="text-2xl font-bold mb-6 text-center">로그인</h1>
        <form onSubmit={signInWithEmail} className="flex flex-col gap-4">
          <input
            type="email"
            placeholder="이메일"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
            className="rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
          />
          <input
            type="password"
            placeholder="비밀번호"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
            minLength={6}
            className="rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
          />
          <button
            type="submit"
            disabled={isLoading}
            className="rounded-lg bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
          >
            로그인
          </button>
        </form>
        <div className="my-5 flex items-center gap-3 text-xs text-zinc-400">
          <span className="h-px flex-1 bg-zinc-200 dark:bg-zinc-700" />
          또는
          <span className="h-px flex-1 bg-zinc-200 dark:bg-zinc-700" />
        </div>
        <button
          type="button"
          onClick={() => void signInWithOAuth("google")}
          disabled={isLoading}
          className="mb-3 flex w-full items-center justify-center gap-2 rounded-lg border border-zinc-300 bg-white px-4 py-3 text-sm font-semibold text-zinc-700 transition-colors hover:bg-zinc-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100 dark:hover:bg-zinc-800"
        >
          <svg aria-hidden="true" viewBox="0 0 24 24" className="h-5 w-5">
            <path fill="#4285F4" d="M21.35 12.27c0-.79-.07-1.55-.2-2.27H12v4.3h5.23a4.47 4.47 0 0 1-1.94 2.93v2.79h3.14c1.84-1.69 2.92-4.18 2.92-7.75Z" />
            <path fill="#34A853" d="M12 21.75c2.62 0 4.81-.87 6.42-2.36l-3.14-2.79c-.87.58-1.99.92-3.28.92-2.53 0-4.67-1.71-5.44-4.01H3.32v2.88A9.7 9.7 0 0 0 12 21.75Z" />
            <path fill="#FBBC05" d="M6.56 13.51A5.85 5.85 0 0 1 6.26 12c0-.52.1-1.02.3-1.51V7.61H3.32A9.7 9.7 0 0 0 2.25 12c0 1.57.38 3.05 1.07 4.39l3.24-2.88Z" />
            <path fill="#EA4335" d="M12 6.48c1.43 0 2.71.49 3.72 1.45l2.79-2.79C16.81 3.55 14.62 2.25 12 2.25a9.7 9.7 0 0 0-8.68 5.36l3.24 2.88c.77-2.3 2.91-4.01 5.44-4.01Z" />
          </svg>
          Google로 로그인
        </button>
        <button
          type="button"
          onClick={() => void signInWithOAuth("kakao")}
          disabled={isLoading}
          className="flex w-full items-center justify-center gap-2 rounded-lg bg-[#FEE500] px-4 py-3 text-sm font-semibold text-[#191919] transition-colors hover:bg-[#f5dc00] disabled:cursor-not-allowed disabled:opacity-60"
        >
          <span aria-hidden="true">💬</span>
          카카오로 로그인
        </button>
        {(error || searchParams.get("error")) && (
          <p role="alert" className="mt-4 text-center text-sm text-red-600">
            {error ?? "로그인에 실패했습니다. 다시 시도해 주세요."}
          </p>
        )}
        <p className="mt-4 text-center text-sm text-zinc-500">
          계정이 없으신가요?{" "}
          <Link href="/signup" className="font-medium text-zinc-900 dark:text-zinc-100">
            회원가입
          </Link>
        </p>
      </div>
    </main>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={<main className="flex flex-1 items-center justify-center px-4 text-sm text-zinc-500">로그인 화면을 불러오고 있습니다...</main>}>
      <LoginForm />
    </Suspense>
  );
}
