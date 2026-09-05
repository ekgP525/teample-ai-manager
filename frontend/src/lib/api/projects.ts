import type { Project } from "@/types/minutes";
import { apiRequest } from "./client";

export interface CreateProjectInput {
  name: string;
  endDate?: string;
}

export interface ProjectMember {
  userId: string;
  displayName: string;
  role: "OWNER" | "MEMBER";
  joinedAt: string;
}

export function getProjects(signal?: AbortSignal) {
  return apiRequest<Project[]>("/api/projects", {
    errorMessage: "프로젝트를 불러오지 못했습니다.",
    signal,
  });
}

export function getDeletedProjects(signal?: AbortSignal) {
  return apiRequest<Project[]>("/api/projects/trash", {
    errorMessage: "삭제된 프로젝트를 불러오지 못했습니다.",
    signal,
  });
}

export function getProject(projectId: string, signal?: AbortSignal) {
  return apiRequest<Project>(`/api/projects/${encodeURIComponent(projectId)}`, {
    errorMessage: "프로젝트를 불러오지 못했습니다.",
    signal,
  });
}

export function getProjectMembers(projectId: string, signal?: AbortSignal) {
  return apiRequest<ProjectMember[]>(
    `/api/projects/${encodeURIComponent(projectId)}/members`,
    {
      errorMessage: "프로젝트 팀원을 불러오지 못했습니다.",
      signal,
    }
  );
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

export function restoreProject(projectId: string) {
  return apiRequest<Project>(
    `/api/projects/${encodeURIComponent(projectId)}/restore`,
    {
      method: "PATCH",
      errorMessage: "프로젝트를 복원하지 못했습니다.",
    }
  );
}

export function permanentlyDeleteProject(projectId: string) {
  return apiRequest<void>(
    `/api/projects/${encodeURIComponent(projectId)}/permanent`,
    {
      method: "DELETE",
      errorMessage: "프로젝트를 영구 삭제하지 못했습니다.",
    }
  );
}
