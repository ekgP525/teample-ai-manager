import type { Minutes, MinutesSummary, Todo } from "@/types/minutes";
import { apiRequest } from "./client";

export interface CreateMinutesInput {
  title: string;
  meetingDate: string;
  rawText: string;
}

export interface UpdateMinutesInput {
  title: string;
  topic: string;
  discussions: string[];
  decisions: string[];
  pending: string[];
  todos: Todo[];
  nextAgenda: string[];
}

function minutesPath(projectId: string) {
  return `/api/projects/${encodeURIComponent(projectId)}/minutes`;
}

function minutesDetailPath(projectId: string, minutesId: string) {
  return `${minutesPath(projectId)}/${encodeURIComponent(minutesId)}`;
}

export function getProjectMinutes(projectId: string, signal?: AbortSignal) {
  return apiRequest<MinutesSummary[]>(minutesPath(projectId), {
    errorMessage: "회의록 목록을 불러오지 못했습니다.",
    signal,
  });
}

export function getMinutes(
  projectId: string,
  minutesId: string,
  options: Pick<RequestInit, "cache" | "signal"> = {}
) {
  return apiRequest<Minutes>(minutesDetailPath(projectId, minutesId), {
    ...options,
    errorMessage: "회의록을 불러오지 못했습니다.",
  });
}

export function createMinutes(projectId: string, input: CreateMinutesInput) {
  return apiRequest<Minutes>(minutesPath(projectId), {
    method: "POST",
    body: JSON.stringify(input),
    errorMessage: "회의록 생성에 실패했습니다.",
  });
}

export function updateMinutes(
  projectId: string,
  minutesId: string,
  input: UpdateMinutesInput
) {
  return apiRequest<Minutes>(minutesDetailPath(projectId, minutesId), {
    method: "PUT",
    body: JSON.stringify(input),
    errorMessage: "회의록 저장에 실패했습니다.",
  });
}

export function deleteMinutes(projectId: string, minutesId: string) {
  return apiRequest<void>(minutesDetailPath(projectId, minutesId), {
    method: "DELETE",
    errorMessage: "회의록 삭제에 실패했습니다.",
  });
}
