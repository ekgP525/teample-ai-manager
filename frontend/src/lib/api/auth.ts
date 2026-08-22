import { apiRequest } from "./client";

export interface CurrentAuthUser {
  authUserId: string;
  memberKey: string;
  email: string | null;
  admin: boolean;
  authMode: "SUPABASE" | "ADMIN_TEST";
}

export function getCurrentAuthUser(signal?: AbortSignal) {
  return apiRequest<CurrentAuthUser>("/api/auth/me", {
    errorMessage: "로그인 사용자 정보를 확인하지 못했습니다.",
    signal,
  });
}
