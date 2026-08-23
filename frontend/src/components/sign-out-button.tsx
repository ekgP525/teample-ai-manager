"use client";

import { clearAdminTestSession } from "@/lib/admin-test-auth";
import { supabase } from "@/lib/supabase";
import { useRouter } from "next/navigation";

export function SignOutButton({ className = "" }: { className?: string }) {
  const router = useRouter();

  async function signOut() {
    clearAdminTestSession();
    await supabase.auth.signOut();
    router.replace("/login");
    router.refresh();
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
