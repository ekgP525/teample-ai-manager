"use client";

import { supabase } from "@/lib/supabase";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

export function AuthSessionGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [isChecking, setIsChecking] = useState(true);

  useEffect(() => {
    let isMounted = true;

    async function checkSession() {
      const { data } = await supabase.auth.getSession();
      if (!data.session) {
        router.replace(`/login?next=${encodeURIComponent(pathname)}`);
        return;
      }
      if (isMounted) setIsChecking(false);
    }

    void checkSession();
    return () => {
      isMounted = false;
    };
  }, [pathname, router]);

  if (isChecking) {
    return <main className="p-8 text-center text-sm text-zinc-500">로그인 정보를 확인하고 있습니다...</main>;
  }

  return <>{children}</>;
}
