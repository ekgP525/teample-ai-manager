"use client";

import { supabase } from "@/lib/supabase";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

export function AuthSessionGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [isChecking, setIsChecking] = useState(true);
  const [checkError, setCheckError] = useState("");

  useEffect(() => {
    let isMounted = true;

    async function checkSession() {
      try {
        const { data, error } = await supabase.auth.getSession();
        if (error) throw error;
        if (!data.session) {
          router.replace(`/login?next=${encodeURIComponent(pathname)}`);
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

  if (checkError) {
    return (
      <main className="flex flex-1 items-center justify-center px-4 py-12">
        <div role="alert" className="text-center">
          <p className="text-sm text-red-600 dark:text-red-400">
            {checkError}
          </p>
          <button
            type="button"
            onClick={() => window.location.reload()}
            className="mt-4 rounded-lg border border-zinc-300 px-3 py-1.5 text-sm transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
          >
            다시 시도
          </button>
        </div>
      </main>
    );
  }

  if (isChecking) {
    return <main className="p-8 text-center text-sm text-zinc-500">로그인 정보를 확인하고 있습니다...</main>;
  }

  return <>{children}</>;
}
