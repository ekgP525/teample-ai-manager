"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import {
  getProjectTodos,
  reorderProjectTodos,
  updateProjectTodoStatus,
  type ProjectTodoStatus,
} from "@/lib/api/todos";
import type { ProjectTodo } from "@/types/minutes";

interface TodoTransitionToast {
  todo: ProjectTodo;
  previousStatus: ProjectTodoStatus;
  originalIndex: number;
  message: string;
}

export function ProjectTodoBoard({ projectId }: { projectId: string }) {
  const [status, setStatus] = useState<ProjectTodoStatus>("TODO");
  const [todos, setTodos] = useState<ProjectTodo[]>([]);
  const [loadedKey, setLoadedKey] = useState("");
  const [error, setError] = useState("");
  const [pendingTodoId, setPendingTodoId] = useState("");
  const [isReordering, setIsReordering] = useState(false);
  const [exitingTodoId, setExitingTodoId] = useState("");
  const [toast, setToast] = useState<TodoTransitionToast | null>(null);
  const [isToastLeaving, setIsToastLeaving] = useState(false);
  const [isUndoing, setIsUndoing] = useState(false);
  const exitTimerRef = useRef<number | null>(null);
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

  useEffect(() => {
    if (!toast || isUndoing) return;

    const leaveTimer = window.setTimeout(() => setIsToastLeaving(true), 3600);
    const removeTimer = window.setTimeout(() => setToast(null), 4000);
    return () => {
      window.clearTimeout(leaveTimer);
      window.clearTimeout(removeTimer);
    };
  }, [isUndoing, toast]);

  useEffect(() => {
    return () => {
      if (exitTimerRef.current !== null) {
        window.clearTimeout(exitTimerRef.current);
      }
    };
  }, []);

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
    if (pendingTodoId || exitingTodoId) return;

    const previousStatus = todo.status;
    const nextStatus: ProjectTodoStatus =
      todo.status === "TODO" ? "COMPLETED" : "TODO";
    const originalIndex = todos.findIndex((item) => item.id === todo.id);
    setPendingTodoId(todo.id);
    setError("");
    setTodos((current) =>
      current.map((item) =>
        item.id === todo.id ? { ...item, status: nextStatus } : item
      )
    );

    try {
      await updateProjectTodoStatus(projectId, todo.id, nextStatus);
      setExitingTodoId(todo.id);
      setToast({
        todo,
        previousStatus,
        originalIndex,
        message:
          nextStatus === "COMPLETED"
            ? "업무를 완료했어요."
            : "진행 중으로 되돌렸어요.",
      });
      setIsToastLeaving(false);

      exitTimerRef.current = window.setTimeout(() => {
        setTodos((current) => current.filter((item) => item.id !== todo.id));
        setExitingTodoId("");
        exitTimerRef.current = null;
      }, 320);
    } catch (updateError) {
      setTodos((current) =>
        current.map((item) =>
          item.id === todo.id ? { ...item, status: previousStatus } : item
        )
      );
      setError(
        updateError instanceof Error
          ? updateError.message
          : "업무 상태를 변경하지 못했습니다."
      );
    } finally {
      setPendingTodoId("");
    }
  };

  const handleUndo = async () => {
    if (!toast || isUndoing) return;

    const transition = toast;
    const restoredTodo = {
      ...transition.todo,
      status: transition.previousStatus,
    };

    if (exitTimerRef.current !== null) {
      window.clearTimeout(exitTimerRef.current);
      exitTimerRef.current = null;
    }

    setIsUndoing(true);
    setExitingTodoId("");
    setError("");
    setTodos((current) => {
      if (current.some((item) => item.id === restoredTodo.id)) {
        return current.map((item) =>
          item.id === restoredTodo.id ? restoredTodo : item
        );
      }

      if (status !== transition.previousStatus) return current;

      const restored = [...current];
      restored.splice(
        Math.min(transition.originalIndex, restored.length),
        0,
        restoredTodo
      );
      return restored;
    });

    try {
      await updateProjectTodoStatus(
        projectId,
        transition.todo.id,
        transition.previousStatus
      );
    } catch (undoError) {
      setTodos((current) =>
        current.filter((item) => item.id !== transition.todo.id)
      );
      setError(
        undoError instanceof Error
          ? undoError.message
          : "업무 상태를 되돌리지 못했습니다."
      );
    } finally {
      setToast(null);
      setIsUndoing(false);
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
                setToast(null);
                setIsToastLeaving(false);
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
        <div aria-busy={isReordering}>
          {todos.map((todo, index) => (
            <div
              key={todo.id}
              className={`grid transition-[grid-template-rows,opacity,transform] duration-300 ease-out motion-reduce:transition-none ${
                exitingTodoId === todo.id
                  ? "grid-rows-[0fr] -translate-x-2 opacity-0"
                  : "grid-rows-[1fr] opacity-100"
              }`}
            >
              <div className="min-h-0 overflow-hidden">
                <div className="group mb-2 flex items-start gap-3 rounded-xl border border-zinc-200 bg-white p-3.5 shadow-sm transition-[border-color,box-shadow] hover:border-zinc-300 hover:shadow-md dark:border-zinc-700 dark:bg-zinc-950 dark:hover:border-zinc-600">
                  <button
                    type="button"
                    onClick={() => void handleStatusChange(todo)}
                    disabled={Boolean(pendingTodoId || exitingTodoId)}
                    aria-label={todo.status === "TODO" ? "업무 완료 처리" : "업무 복원"}
                    className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded border text-xs transition-[background-color,border-color,transform] duration-200 disabled:cursor-wait disabled:opacity-50 ${
                      todo.status === "COMPLETED"
                        ? "scale-105 border-emerald-600 bg-emerald-600 text-white"
                        : "border-zinc-300 hover:border-emerald-500 dark:border-zinc-600"
                    }`}
                  >
                    {todo.status === "COMPLETED" ? "✓" : ""}
                  </button>
                  <div className="min-w-0 flex-1">
                    <p className={`break-words text-sm transition-colors ${todo.status === "COMPLETED" ? "text-zinc-400 line-through" : "font-medium"}`}>
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
                    <div className="flex shrink-0 items-center gap-2">
                      <span
                        className="w-5 text-center text-xs font-semibold tabular-nums text-zinc-400"
                        aria-label={`우선순위 ${index + 1}`}
                      >
                        {index + 1}
                      </span>
                      <div className="flex overflow-hidden rounded-lg border border-zinc-200 bg-zinc-50 shadow-sm dark:border-zinc-700 dark:bg-zinc-900">
                        <MoveButton
                          direction="up"
                          onClick={() => void handleMove(index, -1)}
                          disabled={index === 0 || isReordering || Boolean(pendingTodoId)}
                        />
                        <MoveButton
                          direction="down"
                          onClick={() => void handleMove(index, 1)}
                          disabled={index === todos.length - 1 || isReordering || Boolean(pendingTodoId)}
                        />
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {toast && (
        <div
          role="status"
          className={`fixed bottom-5 left-1/2 z-50 flex w-[calc(100%-2rem)] max-w-sm -translate-x-1/2 items-center gap-3 rounded-xl border border-zinc-700 bg-zinc-900 px-4 py-3 text-sm text-white shadow-2xl transition-[opacity,transform] duration-300 motion-reduce:transition-none dark:border-zinc-600 dark:bg-zinc-100 dark:text-zinc-900 ${
            isToastLeaving
              ? "translate-y-2 opacity-0"
              : "translate-y-0 opacity-100"
          }`}
        >
          <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-emerald-500 text-xs font-bold text-white">
            ✓
          </span>
          <span className="min-w-0 flex-1">{toast.message}</span>
          <button
            type="button"
            onClick={() => void handleUndo()}
            disabled={isUndoing}
            className="shrink-0 rounded-md px-2 py-1 font-semibold text-emerald-300 transition-colors hover:bg-white/10 disabled:cursor-wait disabled:opacity-50 dark:text-emerald-700 dark:hover:bg-black/5"
          >
            {isUndoing ? "되돌리는 중..." : "실행 취소"}
          </button>
        </div>
      )}
    </section>
  );
}

function MoveButton({
  direction,
  onClick,
  disabled,
}: {
  direction: "up" | "down";
  onClick: () => void;
  disabled: boolean;
}) {
  const isUp = direction === "up";

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={`업무 우선순위 ${isUp ? "올리기" : "내리기"}`}
      title={`우선순위 ${isUp ? "올리기" : "내리기"}`}
      className="flex h-8 w-8 items-center justify-center text-zinc-500 transition-colors first:border-r first:border-zinc-200 hover:bg-white hover:text-zinc-950 focus-visible:z-10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:cursor-not-allowed disabled:opacity-25 dark:first:border-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-100"
    >
      <svg
        viewBox="0 0 20 20"
        aria-hidden="true"
        className="h-4 w-4"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        {isUp ? <path d="m5.5 12.5 4.5-4.5 4.5 4.5" /> : <path d="m5.5 7.5 4.5 4.5 4.5-4.5" />}
      </svg>
    </button>
  );
}
