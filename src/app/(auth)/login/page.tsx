"use client";

import Link from "next/link";

export default function LoginPage() {
  // TODO: Supabase Auth 연동
  return (
    <main className="flex flex-1 flex-col items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <h1 className="text-2xl font-bold mb-6 text-center">로그인</h1>
        <form className="flex flex-col gap-4">
          <input
            type="email"
            placeholder="이메일"
            className="rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
          />
          <input
            type="password"
            placeholder="비밀번호"
            className="rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
          />
          <button
            type="submit"
            className="rounded-lg bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
          >
            로그인
          </button>
        </form>
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
