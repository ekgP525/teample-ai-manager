"use client";

import { clearAdminTestSession } from "@/lib/admin-test-auth";
import { supabase } from "@/lib/supabase";
import { useRouter } from "next/navigation";

export function SignOutButton({ className = "" }: { className?: string }) {
  const router = useRouter();

  async function signOut() {
    clearAdminTestSession();
    try {
      await supabase.auth.signOut();
    } catch {
      // Supabase에 닿지 않아도 로컬 세션은 정리되므로 로그인 화면으로 보낸다.
    } finally {
      router.replace("/login");
      router.refresh();
    }
  }

  return (
    <button
      type="button"
      onClick={() => void signOut()}
      className={`text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100 ${className}`}
    >
      로그아웃
    </button>
  );
}
