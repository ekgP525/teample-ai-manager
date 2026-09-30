import type { KakaoConnectUrl, KakaoLinkStatus } from "@/types/subscription";
import { apiRequest } from "./client";

export function getKakaoLink(signal?: AbortSignal) {
  return apiRequest<KakaoLinkStatus>("/api/me/kakao", {
    errorMessage: "카카오 연결 상태를 불러오지 못했습니다.",
    signal,
  });
}

export function getKakaoConnectUrl(redirectUri: string) {
  return apiRequest<KakaoConnectUrl>(
    `/api/me/kakao/connect-url?redirectUri=${encodeURIComponent(redirectUri)}`,
    {
      errorMessage: "카카오 연결을 시작하지 못했습니다.",
    }
  );
}

export function linkKakao(code: string, redirectUri: string, state: string | null) {
  return apiRequest<KakaoLinkStatus>("/api/me/kakao/link", {
    method: "POST",
    body: JSON.stringify({ code, redirectUri, state }),
    errorMessage: "카카오를 연결하지 못했습니다.",
  });
}

export function updateKakaoPreferences(deadlineReminders: boolean) {
  return apiRequest<KakaoLinkStatus>("/api/me/kakao/preferences", {
    method: "PUT",
    body: JSON.stringify({ deadlineReminders }),
    errorMessage: "알림 설정을 저장하지 못했습니다.",
  });
}

export function sendKakaoTestMessage() {
  return apiRequest<void>("/api/me/kakao/test", {
    method: "POST",
    errorMessage: "테스트 메시지를 보내지 못했습니다.",
  });
}

export function unlinkKakao() {
  return apiRequest<void>("/api/me/kakao", {
    method: "DELETE",
    errorMessage: "카카오 연결을 해제하지 못했습니다.",
  });
}
