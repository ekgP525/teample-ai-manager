import type { ProjectTodo } from "@/types/minutes";
import { apiRequest } from "./client";

export type ProjectTodoStatus = ProjectTodo["status"];

function projectTodosPath(projectId: string) {
  return `/api/projects/${encodeURIComponent(projectId)}/todos`;
}

export function getProjectTodos(
  projectId: string,
  status: ProjectTodoStatus,
  signal?: AbortSignal
) {
  const query = new URLSearchParams({ status });
  return apiRequest<ProjectTodo[]>(`${projectTodosPath(projectId)}?${query}`, {
    errorMessage: "프로젝트 업무를 불러오지 못했습니다.",
    signal,
  });
}

export function updateProjectTodoStatus(
  projectId: string,
  todoId: string,
  status: ProjectTodoStatus
) {
  return apiRequest<ProjectTodo>(
    `${projectTodosPath(projectId)}/${encodeURIComponent(todoId)}/status`,
    {
      method: "PATCH",
      body: JSON.stringify({ status }),
      errorMessage: "업무 상태를 변경하지 못했습니다.",
    }
  );
}

export function reorderProjectTodos(projectId: string, orderedTodoIds: string[]) {
  return apiRequest<ProjectTodo[]>(`${projectTodosPath(projectId)}/order`, {
    method: "PUT",
    body: JSON.stringify({ orderedTodoIds }),
    errorMessage: "업무 순서를 변경하지 못했습니다.",
  });
}
