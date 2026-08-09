"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import {
  getProjectTodos,
  reorderProjectTodos,
  updateProjectTodoStatus,
  type ProjectTodoStatus,
} from "@/lib/api/todos";
import type { ProjectTodo } from "@/types/minutes";

export function ProjectTodoBoard({ projectId }: { projectId: string }) {
  const [status, setStatus] = useState<ProjectTodoStatus>("TODO");
  const [todos, setTodos] = useState<ProjectTodo[]>([]);
  const [loadedKey, setLoadedKey] = useState("");
  const [error, setError] = useState("");
  const [pendingTodoId, setPendingTodoId] = useState("");
  const [isReordering, setIsReordering] = useState(false);
  const currentKey = `${projectId}/${status}`;

  useEffect(() => {
    const controller = new AbortController();

    void getProjectTodos(projectId, status, controller.signal)
      .then((data) => {
        setTodos(data);
        setError("");
      })
      .catch((loadError: unknown) => {
        if (!controller.signal.aborted) {
          setTodos([]);
          setError(
            loadError instanceof Error
              ? loadError.message
              : "프로젝트 업무를 불러오지 못했습니다."
          );
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoadedKey(currentKey);
      });

    return () => controller.abort();
  }, [currentKey, projectId, status]);

  const reload = async () => {
    setLoadedKey("");
    setError("");
    try {
      setTodos(await getProjectTodos(projectId, status));
    } catch (loadError) {
      setTodos([]);
      setError(
        loadError instanceof Error
          ? loadError.message
          : "프로젝트 업무를 불러오지 못했습니다."
      );
    } finally {
      setLoadedKey(currentKey);
    }
  };

  const handleStatusChange = async (todo: ProjectTodo) => {
    const nextStatus: ProjectTodoStatus =
      todo.status === "TODO" ? "COMPLETED" : "TODO";
    setPendingTodoId(todo.id);
    setError("");

    try {
      await updateProjectTodoStatus(projectId, todo.id, nextStatus);
      setTodos((current) => current.filter((item) => item.id !== todo.id));
    } catch (updateError) {
      setError(
        updateError instanceof Error
          ? updateError.message
          : "업무 상태를 변경하지 못했습니다."
      );
    } finally {
      setPendingTodoId("");
    }
  };

  const handleMove = async (index: number, direction: -1 | 1) => {
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= todos.length || isReordering) return;

    const reordered = [...todos];
    [reordered[index], reordered[targetIndex]] = [
      reordered[targetIndex],
      reordered[index],
    ];
    setTodos(reordered);
    setIsReordering(true);
    setError("");

    try {
      setTodos(
        await reorderProjectTodos(
          projectId,
          reordered.map((todo) => todo.id)
        )
      );
    } catch (reorderError) {
      setTodos(todos);
      setError(
        reorderError instanceof Error
          ? reorderError.message
          : "업무 순서를 변경하지 못했습니다."
      );
    } finally {
      setIsReordering(false);
    }
  };

  return (
    <section className="mb-10">
      <div className="mb-3 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-lg font-semibold">프로젝트 업무</h2>
          <p className="mt-0.5 text-xs text-zinc-500">
            회의록에서 추출된 업무를 완료 처리하거나 우선순위대로 정렬할 수 있습니다.
          </p>
        </div>
        <div className="grid grid-cols-2 rounded-lg bg-zinc-100 p-1 text-sm dark:bg-zinc-900">
          {(["TODO", "COMPLETED"] as const).map((itemStatus) => (
            <button
              key={itemStatus}
              type="button"
              onClick={() => {
                setStatus(itemStatus);
                setError("");
              }}
              className={`rounded-md px-3 py-1.5 transition-colors ${
                status === itemStatus
                  ? "bg-white font-medium shadow-sm dark:bg-zinc-700"
                  : "text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100"
              }`}
            >
              {itemStatus === "TODO" ? "진행 중" : "완료"}
            </button>
          ))}
        </div>
      </div>

      {error && (
        <div
          role="alert"
          className="mb-3 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-600 dark:bg-red-950 dark:text-red-400"
        >
          <p>{error}</p>
          <button type="button" onClick={() => void reload()} className="mt-2 underline">
            다시 시도
          </button>
        </div>
      )}

      {loadedKey !== currentKey ? (
        <div className="rounded-lg border border-zinc-200 p-6 text-center dark:border-zinc-700">
          <p aria-live="polite" className="text-sm text-zinc-500">
            업무를 불러오는 중...
          </p>
        </div>
      ) : todos.length === 0 ? (
        <div className="rounded-lg border border-zinc-200 p-6 text-center dark:border-zinc-700">
          <p className="text-sm text-zinc-500">
            {status === "TODO" ? "진행 중인 업무가 없습니다." : "완료한 업무가 없습니다."}
          </p>
        </div>
      ) : (
        <div className="space-y-2">
          {todos.map((todo, index) => (
            <div
              key={todo.id}
              className="flex items-start gap-3 rounded-lg border border-zinc-200 p-3 dark:border-zinc-700"
            >
              <button
                type="button"
                onClick={() => void handleStatusChange(todo)}
                disabled={pendingTodoId === todo.id}
                aria-label={todo.status === "TODO" ? "업무 완료 처리" : "업무 복원"}
                className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded border text-xs disabled:cursor-wait disabled:opacity-50 ${
                  todo.status === "COMPLETED"
                    ? "border-emerald-600 bg-emerald-600 text-white"
                    : "border-zinc-300 hover:border-emerald-500 dark:border-zinc-600"
                }`}
              >
                {todo.status === "COMPLETED" ? "✓" : ""}
              </button>
              <div className="min-w-0 flex-1">
                <p className={`break-words text-sm ${todo.status === "COMPLETED" ? "text-zinc-400 line-through" : "font-medium"}`}>
                  {todo.content}
                </p>
                <div className="mt-1 flex flex-wrap gap-x-2 gap-y-1 text-xs text-zinc-500">
                  <span>담당 {todo.assignee?.name || "미지정"}</span>
                  <span>기한 {todo.dueDate || "미정"}</span>
                  {todo.meetingNoteId && (
                    <Link
                      href={`/projects/${projectId}/minutes/${todo.meetingNoteId}`}
                      className="hover:underline"
                    >
                      회의록 보기
                    </Link>
                  )}
                </div>
              </div>
              {status === "TODO" && (
                <div className="flex shrink-0 gap-1">
                  <button
                    type="button"
                    onClick={() => void handleMove(index, -1)}
                    disabled={index === 0 || isReordering}
                    aria-label="업무 우선순위 올리기"
                    className="rounded border border-zinc-300 px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-30 dark:border-zinc-700"
                  >
                    ↑
                  </button>
                  <button
                    type="button"
                    onClick={() => void handleMove(index, 1)}
                    disabled={index === todos.length - 1 || isReordering}
                    aria-label="업무 우선순위 내리기"
                    className="rounded border border-zinc-300 px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-30 dark:border-zinc-700"
                  >
                    ↓
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
