import type { UserPlan } from "@/types/transcription";
import { apiRequest } from "./client";

export function getMyPlan(signal?: AbortSignal) {
  return apiRequest<UserPlan>("/api/me/plan", {
    errorMessage: "요금제 정보를 불러오지 못했습니다.",
    signal,
  });
}
