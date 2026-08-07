import type { Project } from "@/types/minutes";
import { apiRequest } from "./client";

export interface CreateProjectInput {
  name: string;
  members: string[];
}

export function getProjects(signal?: AbortSignal) {
  return apiRequest<Project[]>("/api/projects", {
    errorMessage: "프로젝트를 불러오지 못했습니다.",
    signal,
  });
}

export function getProject(projectId: string, signal?: AbortSignal) {
  return apiRequest<Project>(`/api/projects/${encodeURIComponent(projectId)}`, {
    errorMessage: "프로젝트를 불러오지 못했습니다.",
    signal,
  });
}

export function createProject(input: CreateProjectInput) {
  return apiRequest<Project>("/api/projects", {
    method: "POST",
    body: JSON.stringify(input),
    errorMessage: "프로젝트를 생성하지 못했습니다.",
  });
}

export function deleteProject(projectId: string) {
  return apiRequest<void>(`/api/projects/${encodeURIComponent(projectId)}`, {
    method: "DELETE",
    errorMessage: "프로젝트를 삭제하지 못했습니다.",
  });
}
