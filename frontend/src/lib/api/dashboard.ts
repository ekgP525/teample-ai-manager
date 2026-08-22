import type {
  MyProjectDashboard,
  TeamProjectDashboard,
  TodoAssignment,
} from "@/types/dashboard";
import { apiRequest } from "./client";

export function getMyProjectDashboards(signal?: AbortSignal) {
  return apiRequest<MyProjectDashboard[]>("/api/dashboard/projects/my", {
    errorMessage: "내 대시보드를 불러오지 못했습니다.",
    signal,
  });
}

export function getMyProjectDashboard(
  projectId: string,
  signal?: AbortSignal
) {
  return apiRequest<MyProjectDashboard>(
    `/api/projects/${encodeURIComponent(projectId)}/dashboard/my`,
    {
      errorMessage: "내 프로젝트 진행률을 불러오지 못했습니다.",
      signal,
    }
  );
}

export function getTeamProjectDashboard(
  projectId: string,
  signal?: AbortSignal
) {
  return apiRequest<TeamProjectDashboard>(
    `/api/projects/${encodeURIComponent(projectId)}/dashboard/team`,
    {
      errorMessage: "팀 진행률을 불러오지 못했습니다.",
      signal,
    }
  );
}

export function updateTodoAssignment(
  assignmentId: string,
  completed: boolean
) {
  return apiRequest<TodoAssignment>(
    `/api/todo-assignments/${encodeURIComponent(assignmentId)}`,
    {
      method: "PATCH",
      body: JSON.stringify({ completed }),
      errorMessage: "업무 진행 상태를 변경하지 못했습니다.",
    }
  );
}

export function updateMyTodoProgress(
  todoId: string,
  completed: boolean
) {
  return apiRequest<TodoAssignment>(
    `/api/todos/${encodeURIComponent(todoId)}/progress`,
    {
      method: "PATCH",
      body: JSON.stringify({ completed }),
      errorMessage: "업무 진행 상태를 변경하지 못했습니다.",
    }
  );
}
