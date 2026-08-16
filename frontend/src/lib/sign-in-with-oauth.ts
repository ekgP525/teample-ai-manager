import { supabase } from "@/lib/supabase";

export type OAuthProvider = "google" | "kakao";

export function signInWithOAuth(provider: OAuthProvider, next = "/projects") {
  const redirectTo = new URL("/login", window.location.origin);
  redirectTo.searchParams.set("next", next);

  return supabase.auth.signInWithOAuth({
    provider,
    options: { redirectTo: redirectTo.toString() },
  });
}
