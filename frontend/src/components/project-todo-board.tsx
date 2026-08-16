"use client";

import Link from "next/link";
import {
  useEffect,
  useRef,
  useState,
  type DragEvent,
  type KeyboardEvent,
} from "react";
import {
  getProjectTodos,
  reorderProjectTodos,
  updateProjectTodoStatus,
  type ProjectTodoStatus,
} from "@/lib/api/todos";
import type { MinutesSummary, ProjectTodo } from "@/types/minutes";

type TodoViewMode = "all" | "meeting";

type TodoDisplayItem =
  | {
      kind: "heading";
      key: string;
      minutesId: string | null;
      title: string;
      meetingDate: string | null;
      count: number;
    }
  | {
      kind: "todo";
      key: string;
      todo: ProjectTodo;
      index: number;
      position: number;
    };

interface TodoTransitionToast {
  todo: ProjectTodo;
  previousStatus: ProjectTodoStatus;
  originalIndex: number;
  message: string;
}

interface TodoDropTarget {
  todoId: string;
  position: "before" | "after";
}

export function ProjectTodoBoard({
  projectId,
  minutes,
  readOnly = false,
}: {
  projectId: string;
  minutes: MinutesSummary[];
  readOnly?: boolean;
}) {
  const [status, setStatus] = useState<ProjectTodoStatus>("TODO");
  const [viewMode, setViewMode] = useState<TodoViewMode>("meeting");
  const [todos, setTodos] = useState<ProjectTodo[]>([]);
  const [loadedKey, setLoadedKey] = useState("");
  const [error, setError] = useState("");
  const [pendingTodoId, setPendingTodoId] = useState("");
  const [isReordering, setIsReordering] = useState(false);
  const [isEditingOrder, setIsEditingOrder] = useState(false);
  const [orderSnapshot, setOrderSnapshot] = useState<ProjectTodo[]>([]);
  const [draggedTodoId, setDraggedTodoId] = useState("");
  const [dropTarget, setDropTarget] = useState<TodoDropTarget | null>(null);
  const [exitingTodoId, setExitingTodoId] = useState("");
  const [toast, setToast] = useState<TodoTransitionToast | null>(null);
  const [isToastLeaving, setIsToastLeaving] = useState(false);
  const [isUndoing, setIsUndoing] = useState(false);
  const exitTimerRef = useRef<number | null>(null);
  const currentKey = `${projectId}/${status}`;
  const displayItems = buildTodoDisplayItems(todos, minutes, viewMode);
  const canReorderTodos = canReorder(todos, viewMode);

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
    if (readOnly || pendingTodoId || exitingTodoId || isEditingOrder) return;

    const previousStatus = todo.status;
    const nextStatus: ProjectTodoStatus =
      todo.status === "TODO" ? "COMPLETED" : "TODO";
    const originalIndex = todos.findIndex((item) => item.id === todo.id);
    const isLastTodo = todos.length === 1;
    const feedbackStartedAt = performance.now();
    setPendingTodoId(todo.id);
    setError("");
    setTodos((current) =>
      current.map((item) =>
        item.id === todo.id ? { ...item, status: nextStatus } : item
      )
    );

    try {
      await updateProjectTodoStatus(projectId, todo.id, nextStatus);

      const remainingFeedbackTime = Math.max(
        0,
        160 - (performance.now() - feedbackStartedAt)
      );
      if (remainingFeedbackTime > 0) {
        await new Promise((resolve) =>
          window.setTimeout(resolve, remainingFeedbackTime)
        );
      }

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

      if (isLastTodo) {
        setTodos((current) => current.filter((item) => item.id !== todo.id));
        return;
      }

      setExitingTodoId(todo.id);
      exitTimerRef.current = window.setTimeout(() => {
        setTodos((current) => current.filter((item) => item.id !== todo.id));
        setExitingTodoId("");
        exitTimerRef.current = null;
      }, 200);
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

  const moveTodo = (todoId: string, targetIndex: number) => {
    setTodos((current) => {
      const sourceIndex = current.findIndex((todo) => todo.id === todoId);
      if (
        sourceIndex < 0 ||
        targetIndex < 0 ||
        targetIndex >= current.length ||
        sourceIndex === targetIndex
      ) {
        return current;
      }

      if (
        viewMode === "meeting" &&
        getMeetingKey(current[sourceIndex]) !==
          getMeetingKey(current[targetIndex])
      ) {
        return current;
      }

      const reordered = [...current];
      const [movedTodo] = reordered.splice(sourceIndex, 1);
      reordered.splice(targetIndex, 0, movedTodo);
      return reordered;
    });
  };

  const startOrderEditing = () => {
    setOrderSnapshot(todos);
    if (viewMode === "meeting") {
      setTodos(
        buildTodoDisplayItems(todos, minutes, viewMode).flatMap((item) =>
          item.kind === "todo" ? [item.todo] : []
        )
      );
    }
    setIsEditingOrder(true);
    setError("");
  };

  const cancelOrderEditing = () => {
    setTodos(orderSnapshot);
    setOrderSnapshot([]);
    setIsEditingOrder(false);
    setDraggedTodoId("");
    setDropTarget(null);
    setError("");
  };

  const saveOrder = async () => {
    setIsReordering(true);
    setError("");

    try {
      setTodos(
        await reorderProjectTodos(
          projectId,
          todos.map((todo) => todo.id)
        )
      );
      setOrderSnapshot([]);
      setIsEditingOrder(false);
      setDraggedTodoId("");
      setDropTarget(null);
    } catch (reorderError) {
      setError(
        reorderError instanceof Error
          ? reorderError.message
          : "업무 순서를 변경하지 못했습니다."
      );
    } finally {
      setIsReordering(false);
    }
  };

  const handleDragStart = (event: DragEvent<HTMLElement>, todoId: string) => {
    setDraggedTodoId(todoId);
    event.dataTransfer.effectAllowed = "move";
    event.dataTransfer.setData("text/plain", todoId);

    const card = event.currentTarget.closest("[data-todo-card]");
    if (card instanceof HTMLElement) {
      event.dataTransfer.setDragImage(card, 24, 24);
    }
  };

  const handleDragOver = (event: DragEvent<HTMLElement>, targetTodoId: string) => {
    if (!draggedTodoId || draggedTodoId === targetTodoId) return;

    if (viewMode === "meeting") {
      const draggedTodo = todos.find((todo) => todo.id === draggedTodoId);
      const targetTodo = todos.find((todo) => todo.id === targetTodoId);
      if (
        !draggedTodo ||
        !targetTodo ||
        getMeetingKey(draggedTodo) !== getMeetingKey(targetTodo)
      ) {
        setDropTarget(null);
        return;
      }
    }

    event.preventDefault();
    event.dataTransfer.dropEffect = "move";
    const bounds = event.currentTarget.getBoundingClientRect();
    const position =
      event.clientY < bounds.top + bounds.height / 2 ? "before" : "after";
    setDropTarget({ todoId: targetTodoId, position });
  };

  const handleDrop = (event: DragEvent<HTMLElement>, targetTodoId: string) => {
    event.preventDefault();

    if (!draggedTodoId) {
      finishDragging();
      return;
    }

    const bounds = event.currentTarget.getBoundingClientRect();
    const position =
      dropTarget?.todoId === targetTodoId
        ? dropTarget.position
        : event.clientY < bounds.top + bounds.height / 2
          ? "before"
          : "after";

    setTodos((current) => {
      const sourceIndex = current.findIndex(
        (todo) => todo.id === draggedTodoId
      );
      const targetIndex = current.findIndex((todo) => todo.id === targetTodoId);
      if (sourceIndex < 0 || targetIndex < 0) return current;
      if (
        viewMode === "meeting" &&
        getMeetingKey(current[sourceIndex]) !==
          getMeetingKey(current[targetIndex])
      ) {
        return current;
      }

      let insertionIndex = position === "before" ? targetIndex : targetIndex + 1;
      const reordered = [...current];
      const [movedTodo] = reordered.splice(sourceIndex, 1);
      if (sourceIndex < insertionIndex) insertionIndex -= 1;
      reordered.splice(insertionIndex, 0, movedTodo);
      return reordered;
    });

    finishDragging();
  };

  const finishDragging = () => {
    setDraggedTodoId("");
    setDropTarget(null);
  };

  const handleOrderKeyDown = (
    event: KeyboardEvent<HTMLElement>,
    todoId: string,
    index: number
  ) => {
    let targetIndex: number | null = null;

    if (viewMode === "meeting") {
      const meetingKey = getMeetingKey(todos[index]);
      const meetingIndexes = todos
        .map((todo, todoIndex) => ({ todo, todoIndex }))
        .filter(({ todo }) => getMeetingKey(todo) === meetingKey)
        .map(({ todoIndex }) => todoIndex);
      const meetingPosition = meetingIndexes.indexOf(index);

      if (event.key === "ArrowUp") {
        targetIndex = meetingIndexes[meetingPosition - 1] ?? null;
      }
      if (event.key === "ArrowDown") {
        targetIndex = meetingIndexes[meetingPosition + 1] ?? null;
      }
      if (event.key === "Home") targetIndex = meetingIndexes[0] ?? null;
      if (event.key === "End") {
        targetIndex = meetingIndexes[meetingIndexes.length - 1] ?? null;
      }
    } else {
      if (event.key === "ArrowUp") targetIndex = index - 1;
      if (event.key === "ArrowDown") targetIndex = index + 1;
      if (event.key === "Home") targetIndex = 0;
      if (event.key === "End") targetIndex = todos.length - 1;
    }
    if (targetIndex === null) return;

    event.preventDefault();
    moveTodo(todoId, targetIndex);
  };

  return (
    <section className="mb-10">
      <div className="mb-4">
        <div>
          <h2 className="text-lg font-semibold">프로젝트 업무</h2>
          <p className="mt-0.5 text-xs text-zinc-500">
            {readOnly
              ? "회의록에서 추출된 업무를 확인할 수 있습니다."
              : "회의록에서 추출된 업무를 완료 처리하거나 우선순위대로 정렬할 수 있습니다."}
          </p>
        </div>
      </div>

      <div className="mb-4 flex flex-col gap-2 border-b border-zinc-200 sm:flex-row sm:items-end sm:justify-between dark:border-zinc-800">
        <div className="-mb-px flex items-center">
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
              disabled={Boolean(
                isEditingOrder || pendingTodoId || exitingTodoId
              )}
              className={`border-b-2 px-3 py-2.5 text-sm transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${
                status === itemStatus
                  ? "border-zinc-950 font-semibold text-zinc-950 dark:border-zinc-50 dark:text-zinc-50"
                  : "border-transparent text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100"
              }`}
            >
              {itemStatus === "TODO" ? "진행 중" : "완료"}
            </button>
          ))}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 pb-2 sm:justify-end">
          <label className="flex items-center gap-2 text-xs text-zinc-500">
            <span className="hidden sm:inline">보기 방식</span>
            <select
              value={viewMode}
              onChange={(event) =>
                setViewMode(event.target.value as TodoViewMode)
              }
              disabled={Boolean(
                isEditingOrder || pendingTodoId || exitingTodoId
              )}
              aria-label="업무 보기 방식"
              className="min-w-32 rounded-md border border-zinc-300 bg-white py-1.5 pl-2.5 text-sm font-medium text-zinc-700 transition-colors disabled:cursor-not-allowed disabled:opacity-50 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-200"
            >
              <option value="meeting">회의별 보기</option>
              <option value="all">전체 보기</option>
            </select>
          </label>

          {!readOnly &&
          status === "TODO" &&
          loadedKey === currentKey &&
          canReorderTodos &&
          (isEditingOrder ? (
            <div className="flex items-center gap-2">
              <span className="mr-1 hidden text-xs text-zinc-500 lg:inline">
                {viewMode === "meeting"
                  ? "같은 회의 안에서 순서를 변경하세요"
                  : "핸들을 끌어 순서를 변경하세요"}
              </span>
              <button
                type="button"
                onClick={cancelOrderEditing}
                disabled={isReordering}
                className="rounded-md border border-zinc-300 px-3 py-1.5 text-sm transition-colors hover:bg-zinc-100 disabled:cursor-not-allowed disabled:opacity-50 dark:border-zinc-700 dark:hover:bg-zinc-800"
              >
                취소
              </button>
              <button
                type="button"
                onClick={() => void saveOrder()}
                disabled={isReordering}
                className="rounded-md bg-zinc-900 px-3 py-1.5 text-sm font-medium text-white transition-colors hover:bg-zinc-800 disabled:cursor-wait disabled:opacity-50 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
              >
                {isReordering ? "저장 중..." : "순서 저장"}
              </button>
            </div>
          ) : (
            <button
              type="button"
              onClick={startOrderEditing}
              className="px-1 py-1.5 text-sm font-medium text-zinc-500 transition-colors hover:text-zinc-950 dark:text-zinc-400 dark:hover:text-white"
            >
              순서 편집
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
        <div aria-busy={isReordering || Boolean(pendingTodoId)}>
          {displayItems.map((item) => {
            if (item.kind === "heading") {
              return (
                <div
                  key={item.key}
                  className="mb-3 mt-6 flex flex-col gap-1 border-b border-zinc-200 pb-2 first:mt-0 sm:flex-row sm:items-end sm:justify-between dark:border-zinc-800"
                >
                  <div className="min-w-0">
                    {item.minutesId ? (
                      <Link
                        href={`/projects/${projectId}/minutes/${item.minutesId}`}
                        className="break-words font-semibold transition-colors hover:text-zinc-600 dark:hover:text-zinc-300"
                      >
                        {item.title}
                      </Link>
                    ) : (
                      <h3 className="break-words font-semibold">{item.title}</h3>
                    )}
                    {item.meetingDate && (
                      <p className="mt-0.5 text-xs text-zinc-500">
                        {item.meetingDate}
                      </p>
                    )}
                  </div>
                  <span className="shrink-0 text-xs text-zinc-500">
                    업무 {item.count}개
                  </span>
                </div>
              );
            }

            const { todo, index, position } = item;

            return (
            <div
              key={todo.id}
              onDragOver={(event) => handleDragOver(event, todo.id)}
              onDrop={(event) => handleDrop(event, todo.id)}
              className={`relative grid transition-[grid-template-rows,opacity,transform] duration-200 ease-out motion-reduce:transition-none ${
                exitingTodoId === todo.id
                  ? "grid-rows-[0fr] -translate-x-2 opacity-0"
                  : "grid-rows-[1fr] opacity-100"
              }`}
            >
              {isEditingOrder &&
                dropTarget?.todoId === todo.id &&
                draggedTodoId !== todo.id && (
                  <span
                    aria-hidden="true"
                    className={`pointer-events-none absolute left-3 right-3 z-10 h-0.5 rounded-full bg-blue-500 shadow-[0_0_0_2px_rgba(59,130,246,0.12)] ${
                      dropTarget.position === "before" ? "-top-1" : "bottom-1"
                    }`}
                  />
                )}
              <div className="min-h-0 overflow-hidden">
                <div
                  data-todo-card
                  className={`group mb-2 flex items-start gap-3 rounded-xl border p-3.5 transition-[border-color,background-color,opacity] duration-150 motion-reduce:transition-none ${
                    (pendingTodoId === todo.id || exitingTodoId === todo.id) &&
                    todo.status === "COMPLETED"
                      ? "border-emerald-300 bg-emerald-50/70 dark:border-emerald-800 dark:bg-emerald-950/20"
                      : isEditingOrder
                        ? "border-zinc-300 bg-zinc-50/70 dark:border-zinc-600 dark:bg-zinc-900/60"
                        : "border-zinc-200 bg-white hover:border-zinc-300 hover:bg-zinc-50/60 dark:border-zinc-700 dark:bg-zinc-950 dark:hover:border-zinc-600 dark:hover:bg-zinc-900/40"
                  } ${draggedTodoId === todo.id ? "opacity-35" : "opacity-100"}`}
                >
                  {isEditingOrder && (
                    <OrderHandle
                      todo={todo}
                      index={index}
                      onDragStart={handleDragStart}
                      onDragEnd={finishDragging}
                      onKeyDown={handleOrderKeyDown}
                    />
                  )}
                  <button
                    type="button"
                    onClick={() => void handleStatusChange(todo)}
                    disabled={Boolean(
                      readOnly ||
                        pendingTodoId ||
                        exitingTodoId ||
                        isEditingOrder
                    )}
                    aria-pressed={todo.status === "COMPLETED"}
                    aria-label={todo.status === "TODO" ? "업무 완료 처리" : "업무 복원"}
                    className={`mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-md border transition-[background-color,border-color,transform] duration-150 motion-reduce:transition-none disabled:cursor-not-allowed ${
                      todo.status === "COMPLETED"
                        ? "scale-[1.03] border-emerald-500 bg-emerald-500 text-white"
                        : "border-zinc-300 bg-white text-transparent hover:border-emerald-400 dark:border-zinc-600 dark:bg-zinc-950"
                    }`}
                  >
                    <svg
                      viewBox="0 0 20 20"
                      aria-hidden="true"
                      className={`h-4 w-4 transition-[opacity,transform] duration-150 motion-reduce:transition-none ${
                        todo.status === "COMPLETED"
                          ? "scale-100 opacity-100"
                          : "scale-75 opacity-0"
                      }`}
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2.4"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    >
                      <path d="m5 10.5 3 3 7-7" />
                    </svg>
                  </button>
                  <div className="min-w-0 flex-1">
                    <p
                      className={`break-words text-sm transition-colors duration-150 motion-reduce:transition-none ${
                        todo.status === "COMPLETED"
                          ? "text-zinc-400 line-through"
                          : "font-medium"
                      }`}
                    >
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
                  {isEditingOrder && (
                    <span
                      className="flex h-6 min-w-6 shrink-0 items-center justify-center rounded-md bg-zinc-200/70 px-1.5 text-xs font-semibold tabular-nums text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400"
                      aria-label={`우선순위 ${position + 1}`}
                    >
                      {position + 1}
                    </span>
                  )}
                </div>
              </div>
            </div>
            );
          })}
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

function buildTodoDisplayItems(
  todos: ProjectTodo[],
  minutes: MinutesSummary[],
  viewMode: TodoViewMode
): TodoDisplayItem[] {
  if (viewMode === "all") {
    return todos.map((todo, index) => ({
      kind: "todo",
      key: todo.id,
      todo,
      index,
      position: index,
    }));
  }

  const minutesById = new Map(minutes.map((item) => [item.id, item]));
  const groupedTodos = new Map<string, ProjectTodo[]>();

  for (const todo of todos) {
    const groupKey = todo.meetingNoteId || "unknown";
    const group = groupedTodos.get(groupKey) || [];
    group.push(todo);
    groupedTodos.set(groupKey, group);
  }

  const knownMeetingKeys = minutes
    .map((meeting) => meeting.id)
    .filter((meetingId) => groupedTodos.has(meetingId));
  const remainingKeys = Array.from(groupedTodos.keys()).filter(
    (groupKey) => !knownMeetingKeys.includes(groupKey)
  );

  return [...knownMeetingKeys, ...remainingKeys].flatMap((groupKey) => {
    const group = groupedTodos.get(groupKey) || [];
    const meeting = groupKey === "unknown" ? null : minutesById.get(groupKey);
    const heading: TodoDisplayItem = {
      kind: "heading",
      key: `heading-${groupKey}`,
      minutesId: groupKey === "unknown" ? null : groupKey,
      title: meeting?.title || "회의록 정보 없음",
      meetingDate: meeting?.meetingDate || null,
      count: group.length,
    };

    return [
      heading,
      ...group.map(
        (todo, position): TodoDisplayItem => ({
          kind: "todo",
          key: todo.id,
          todo,
          index: todos.findIndex((item) => item.id === todo.id),
          position,
        })
      ),
    ];
  });
}

function getMeetingKey(todo: ProjectTodo | undefined) {
  return todo?.meetingNoteId || "unknown";
}

function canReorder(todos: ProjectTodo[], viewMode: TodoViewMode) {
  if (viewMode === "all") return todos.length > 1;

  const counts = new Map<string, number>();
  for (const todo of todos) {
    const meetingKey = getMeetingKey(todo);
    const count = (counts.get(meetingKey) || 0) + 1;
    if (count > 1) return true;
    counts.set(meetingKey, count);
  }

  return false;
}

function OrderHandle({
  todo,
  index,
  onDragStart,
  onDragEnd,
  onKeyDown,
}: {
  todo: ProjectTodo;
  index: number;
  onDragStart: (event: DragEvent<HTMLElement>, todoId: string) => void;
  onDragEnd: () => void;
  onKeyDown: (
    event: KeyboardEvent<HTMLElement>,
    todoId: string,
    index: number
  ) => void;
}) {
  return (
    <span
      role="button"
      tabIndex={0}
      draggable
      onDragStart={(event) => onDragStart(event, todo.id)}
      onDragEnd={onDragEnd}
      onKeyDown={(event) => onKeyDown(event, todo.id, index)}
      aria-label={`${todo.content} 순서 이동. 방향키 또는 Home, End 키를 사용할 수 있습니다.`}
      title="끌어서 순서 변경"
      className="mt-0.5 flex h-6 w-5 shrink-0 cursor-grab items-center justify-center text-zinc-400 transition-colors hover:text-zinc-700 focus-visible:rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 active:cursor-grabbing dark:hover:text-zinc-200"
    >
      <svg
        viewBox="0 0 12 18"
        aria-hidden="true"
        className="h-4 w-3"
        fill="currentColor"
      >
        <circle cx="3" cy="3" r="1.25" />
        <circle cx="9" cy="3" r="1.25" />
        <circle cx="3" cy="9" r="1.25" />
        <circle cx="9" cy="9" r="1.25" />
        <circle cx="3" cy="15" r="1.25" />
        <circle cx="9" cy="15" r="1.25" />
      </svg>
    </span>
  );
}
