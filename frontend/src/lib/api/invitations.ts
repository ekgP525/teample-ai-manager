import { apiRequest } from "./client";

export interface ProjectInvitation {
  code: string;
  inviteUrl?: string;
  expiresAt: string;
}

export interface JoinProjectInvitationResponse {
  projectId: string;
  projectName: string;
  role?: "OWNER" | "MEMBER";
}

export async function getActiveProjectInvitation(
  projectId: string,
  options: { signal?: AbortSignal } = {}
) {
  return apiRequest<ProjectInvitation>(
    `/api/projects/${encodeURIComponent(projectId)}/invitations/active`,
    {
      method: "GET",
      signal: options.signal,
      errorMessage: "초대 코드를 불러오지 못했습니다.",
    }
  );
}

export async function createProjectInvitation(projectId: string) {
  return apiRequest<ProjectInvitation>(
    `/api/projects/${encodeURIComponent(projectId)}/invitations`,
    {
      method: "POST",
      errorMessage: "초대 코드를 생성하지 못했습니다.",
    }
  );
}

export async function joinProjectInvitation(code: string) {
  return apiRequest<JoinProjectInvitationResponse>(
    "/api/project-invitations/join",
    {
      method: "POST",
      body: JSON.stringify({ code }),
      errorMessage: "프로젝트에 참여하지 못했습니다.",
    }
  );
}
