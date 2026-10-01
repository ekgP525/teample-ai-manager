import { clearAdminTestSession } from "@/lib/admin-test-auth";

/**
 * Supabase가 브라우저에 남긴 세션(`sb-*` 키)과 관리자 테스트 세션을 네트워크 호출 없이 지운다.
 * Supabase 서버에 닿지 않아 signOut이 끝나지 않을 때 로그인 화면으로 보내기 전에 사용한다.
 */
export function clearLocalAuthStorage() {
  clearAdminTestSession();
  if (typeof window === "undefined") return;

  try {
    const keys: string[] = [];
    for (let index = 0; index < window.localStorage.length; index += 1) {
      const key = window.localStorage.key(index);
      if (key && key.startsWith("sb-")) keys.push(key);
    }
    keys.forEach((key) => window.localStorage.removeItem(key));
  } catch {
    // 스토리지 접근이 막힌 환경에서는 조용히 넘어간다.
  }
}
