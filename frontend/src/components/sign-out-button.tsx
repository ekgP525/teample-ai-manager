"use client";

import { supabase } from "@/lib/supabase";
import { useRouter } from "next/navigation";

export function SignOutButton() {
  const router = useRouter();

  async function signOut() {
    await supabase.auth.signOut();
    router.replace("/login");
    router.refresh();
  }

  return (
    <button
      type="button"
      onClick={() => void signOut()}
      className="text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
    >
      로그아웃
    </button>
  );
}
