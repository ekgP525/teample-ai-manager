import { supabase } from "@/lib/supabase";

export type OAuthProvider = "google" | "kakao";

export function getOAuthErrorMessage(
  provider: OAuthProvider,
  error: { message: string }
) {
  const providerName = provider === "google" ? "Google" : "카카오";
  const message = error.message;

  if (/provider.*not enabled|unsupported provider/i.test(message)) {
    return providerName + " 로그인이 Supabase에서 활성화되지 않았습니다.";
  }
  if (/redirect.*(not allowed|allow list|allowlist)|redirect_to/i.test(message)) {
    return "로그인 리디렉션 주소가 Supabase 허용 목록에 없습니다. 현재 주소: " + window.location.origin + "/login";
  }
  if (/failed to fetch|network|fetch error/i.test(message)) {
    return "Supabase 인증 서버에 연결할 수 없습니다. Supabase URL과 네트워크를 확인해 주세요.";
  }

  return providerName + " 로그인 오류: " + message;
}

export function getOAuthCallbackError(searchParams: URLSearchParams) {
  const description = searchParams.get("error_description");
  if (!description) return null;

  return "OAuth 로그인 오류: " + description;
}

export function signInWithOAuth(provider: OAuthProvider, next = "/projects") {
  const redirectTo = new URL("/login", window.location.origin);
  redirectTo.searchParams.set("next", next);

  return supabase.auth.signInWithOAuth({
    provider,
    options: { redirectTo: redirectTo.toString() },
  });
}
