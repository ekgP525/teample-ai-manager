"use client";

import { hasAdminTestSession } from "@/lib/admin-test-auth";
import { clearLocalAuthStorage } from "@/lib/clear-local-auth";
import { supabase } from "@/lib/supabase";
import { withTimeout } from "@/lib/with-timeout";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

const SESSION_CHECK_TIMEOUT_MS = 8000;

export function AuthSessionGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [isChecking, setIsChecking] = useState(true);
  const [checkError, setCheckError] = useState("");

  useEffect(() => {
    let isMounted = true;

    async function checkSession() {
      try {
        if (hasAdminTestSession()) {
          if (isMounted) setIsChecking(false);
          return;
        }

        // 저장된 세션이 오래됐고 Supabase에 닿지 않으면 getSession이 영영 끝나지 않을 수 있다.
        const { data, error } = await withTimeout(
          supabase.auth.getSession(),
          SESSION_CHECK_TIMEOUT_MS
        );
        if (error) throw error;
        if (!data.session) {
          const currentPath = `${window.location.pathname}${window.location.search}`;
          router.replace(`/login?next=${encodeURIComponent(currentPath)}`);
          return;
        }
        if (isMounted) setIsChecking(false);
      } catch {
        if (isMounted) {
          setCheckError("로그인 정보를 확인하지 못했습니다.");
          setIsChecking(false);
        }
      }
    }

    void checkSession();
    return () => {
      isMounted = false;
    };
  }, [pathname, router]);

  const goToLogin = () => {
    clearLocalAuthStorage();
    router.replace("/login?reason=session-expired");
  };

  if (checkError) {
    return (
      <main className="flex flex-1 items-center justify-center px-4 py-12">
        <div role="alert" className="text-center">
          <p className="text-sm text-red-600 dark:text-red-400">
            {checkError}
          </p>
          <div className="mt-4 flex flex-wrap justify-center gap-2">
            <button
              type="button"
              onClick={() => window.location.reload()}
              className="rounded-lg border border-zinc-300 px-3 py-1.5 text-sm transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
            >
              다시 시도
            </button>
            <button
              type="button"
              onClick={goToLogin}
              className="rounded-lg bg-zinc-900 px-3 py-1.5 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
            >
              로그인 화면으로
            </button>
          </div>
        </div>
      </main>
    );
  }

  if (isChecking) {
    return <main className="p-8 text-center text-sm text-zinc-500">로그인 정보를 확인하고 있습니다...</main>;
  }

  return <>{children}</>;
}
