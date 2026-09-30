"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense } from "react";

export default function BillingFailPage() {
  return (
    <Suspense fallback={null}>
      <BillingFailContent />
    </Suspense>
  );
}

function BillingFailContent() {
  const searchParams = useSearchParams();
  const code = searchParams.get("code");
  const message = searchParams.get("message");

  return (
    <main className="flex flex-1 items-center justify-center px-4 py-12">
      <div
        role="alert"
        className="w-full max-w-lg rounded-lg border border-red-200 bg-red-50 p-6 text-center dark:border-red-900 dark:bg-red-950"
      >
        <h1 className="text-lg font-semibold">카드 등록이 완료되지 않았습니다.</h1>
        <p className="mt-2 text-sm leading-6 text-zinc-600 dark:text-zinc-300">
          {code === "PAY_PROCESS_CANCELED" || code === "USER_CANCEL"
            ? "등록을 취소했습니다. 언제든 다시 시도할 수 있습니다."
            : message || "결제사에서 오류를 돌려주었습니다. 잠시 후 다시 시도해 주세요."}
        </p>
        {code && <p className="mt-1 text-xs text-zinc-500">오류 코드 {code}</p>}
        <Link
          href="/profile"
          className="mt-5 inline-block rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
        >
          프로필로 돌아가기
        </Link>
      </div>
    </main>
  );
}
