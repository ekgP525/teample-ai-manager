import type {
  MyProjectDashboard,
  TeamProjectDashboard,
  TodoAssignment,
} from "@/types/dashboard";
import { apiRequest } from "./client";

function userHeaders(userId: string) {
  return { "X-Current-User-Id": encodeURIComponent(userId.trim()) };
}

export function getMyProjectDashboards(userId: string, signal?: AbortSignal) {
  return apiRequest<MyProjectDashboard[]>("/api/dashboard/projects/my", {
    errorMessage: "내 대시보드를 불러오지 못했습니다.",
    headers: userHeaders(userId),
    signal,
  });
}

export function getMyProjectDashboard(
  projectId: string,
  userId: string,
  signal?: AbortSignal
) {
  return apiRequest<MyProjectDashboard>(
    `/api/projects/${encodeURIComponent(projectId)}/dashboard/my`,
    {
      errorMessage: "내 프로젝트 진행률을 불러오지 못했습니다.",
      headers: userHeaders(userId),
      signal,
    }
  );
}

export function getTeamProjectDashboard(
  projectId: string,
  userId: string,
  signal?: AbortSignal
) {
  return apiRequest<TeamProjectDashboard>(
    `/api/projects/${encodeURIComponent(projectId)}/dashboard/team`,
    {
      errorMessage: "팀 진행률을 불러오지 못했습니다.",
      headers: userHeaders(userId),
      signal,
    }
  );
}

export function updateTodoAssignment(
  assignmentId: string,
  userId: string,
  completed: boolean
) {
  return apiRequest<TodoAssignment>(
    `/api/todo-assignments/${encodeURIComponent(assignmentId)}`,
    {
      method: "PATCH",
      body: JSON.stringify({ completed }),
      headers: userHeaders(userId),
      errorMessage: "업무 진행 상태를 변경하지 못했습니다.",
    }
  );
}

export function updateMyTodoProgress(
  todoId: string,
  userId: string,
  completed: boolean
) {
  return apiRequest<TodoAssignment>(
    `/api/todos/${encodeURIComponent(todoId)}/progress`,
    {
      method: "PATCH",
      body: JSON.stringify({ completed }),
      headers: userHeaders(userId),
      errorMessage: "업무 진행 상태를 변경하지 못했습니다.",
    }
  );
}
