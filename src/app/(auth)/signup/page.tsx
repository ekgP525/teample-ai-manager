"use client";

import Link from "next/link";

export default function SignupPage() {
  // TODO: Supabase Auth 연동
  return (
    <main className="flex flex-1 flex-col items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <h1 className="text-2xl font-bold mb-6 text-center">회원가입</h1>
        <form className="flex flex-col gap-4">
          <input
            type="text"
            placeholder="이름"
            className="rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
          />
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
            회원가입
          </button>
        </form>
        <p className="mt-4 text-center text-sm text-zinc-500">
          이미 계정이 있으신가요?{" "}
          <Link href="/login" className="font-medium text-zinc-900 dark:text-zinc-100">
            로그인
          </Link>
        </p>
      </div>
    </main>
  );
}
