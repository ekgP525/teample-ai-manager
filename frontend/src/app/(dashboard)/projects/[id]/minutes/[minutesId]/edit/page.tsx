"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { ApiError } from "@/lib/api/client";
import { getMinutes, updateMinutes } from "@/lib/api/minutes";
import { getProject } from "@/lib/api/projects";
import type { Minutes, Todo } from "@/types/minutes";

export default function EditMinutesPage() {
  const router = useRouter();
  const { id, minutesId } = useParams<{ id: string; minutesId: string }>();

  const [title, setTitle] = useState("");
  const [topic, setTopic] = useState("");
  const [discussions, setDiscussions] = useState<string[]>([]);
  const [decisions, setDecisions] = useState<string[]>([]);
  const [pending, setPending] = useState<string[]>([]);
  const [todos, setTodos] = useState<Todo[]>([]);
  const [nextAgenda, setNextAgenda] = useState<string[]>([]);
  const [loadedKey, setLoadedKey] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [saveError, setSaveError] = useState("");
  const [minutesNotFound, setMinutesNotFound] = useState(false);
  const [isProjectDeleted, setIsProjectDeleted] = useState(false);
  const currentKey = `${id}/${minutesId}`;

  const applyMinutes = (data: Minutes) => {
    setTitle(data.title || "");
    setTopic(data.topic || "");
    setDiscussions(data.discussions || []);
    setDecisions(data.decisions || []);
    setPending(data.pending || []);
    setTodos(data.todos || []);
    setNextAgenda(data.nextAgenda || []);
  };

  const loadMinutes = useCallback(
    async (signal?: AbortSignal) => {
      try {
        const [minutesData, projectData] = await Promise.all([
          getMinutes(id, minutesId, { signal }),
          getProject(id, signal),
        ]);
        applyMinutes(minutesData);
        setIsProjectDeleted(projectData.status === "DELETED");
        setLoadError("");
        setMinutesNotFound(false);
      } catch (error) {
        if (signal?.aborted) return;

        if (error instanceof ApiError && error.status === 404) {
          setMinutesNotFound(true);
        } else {
          setLoadError(
            error instanceof Error
              ? error.message
              : "회의록을 불러오지 못했습니다."
          );
        }
      } finally {
        if (!signal?.aborted) {
          setLoadedKey(currentKey);
          setIsLoading(false);
        }
      }
    },
    [currentKey, id, minutesId]
  );

  const handleRetry = () => {
    setIsLoading(true);
    setLoadError("");
    setMinutesNotFound(false);
    void loadMinutes();
  };

  useEffect(() => {
    const controller = new AbortController();

    void Promise.all([
      getMinutes(id, minutesId, { signal: controller.signal }),
      getProject(id, controller.signal),
    ])
      .then(([minutesData, projectData]) => {
        setTitle(minutesData.title || "");
        setTopic(minutesData.topic || "");
        setDiscussions(minutesData.discussions || []);
        setDecisions(minutesData.decisions || []);
        setPending(minutesData.pending || []);
        setTodos(minutesData.todos || []);
        setNextAgenda(minutesData.nextAgenda || []);
        setIsProjectDeleted(projectData.status === "DELETED");
        setLoadError("");
        setMinutesNotFound(false);
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;

        if (error instanceof ApiError && error.status === 404) {
          setMinutesNotFound(true);
        } else {
          setLoadError(
            error instanceof Error
              ? error.message
              : "회의록을 불러오지 못했습니다."
          );
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoadedKey(currentKey);
          setIsLoading(false);
        }
      });

    return () => controller.abort();
  }, [currentKey, id, minutesId]);

  const handleSave = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (isSaving) return;

    const trimmedTitle = title.trim();
    const trimmedTopic = topic.trim();

    if (!trimmedTitle) {
      setSaveError("회의록 제목을 입력해주세요.");
      return;
    }

    if (!trimmedTopic) {
      setSaveError("회의 주제를 입력해주세요.");
      return;
    }

    setIsSaving(true);
    setSaveError("");

    try {
      await updateMinutes(id, minutesId, {
        title: trimmedTitle,
        topic: trimmedTopic,
        discussions: normalizeItems(discussions),
        decisions: normalizeItems(decisions),
        pending: normalizeItems(pending),
        todos: todos
          .map((todo) => ({
            name: todo.name.trim(),
            task: todo.task.trim(),
            deadline: todo.deadline.trim(),
          }))
          .filter((todo) => todo.name || todo.task || todo.deadline),
        nextAgenda: normalizeItems(nextAgenda),
      });
      router.push(`/projects/${id}/minutes/${minutesId}`);
    } catch (error) {
      setSaveError(
        error instanceof Error ? error.message : "회의록 저장에 실패했습니다."
      );
    } finally {
      setIsSaving(false);
    }
  };

  const updateItem = (
    list: string[],
    setList: (value: string[]) => void,
    index: number,
    value: string
  ) => {
    const next = [...list];
    next[index] = value;
    setList(next);
  };

  const addItem = (list: string[], setList: (value: string[]) => void) => {
    setList([...list, ""]);
  };

  const removeItem = (
    list: string[],
    setList: (value: string[]) => void,
    index: number
  ) => {
    setList(list.filter((_, itemIndex) => itemIndex !== index));
  };

  const updateTodo = (index: number, field: keyof Todo, value: string) => {
    const next = [...todos];
    next[index] = { ...next[index], [field]: value };
    setTodos(next);
  };

  if (isLoading || loadedKey !== currentKey) {
    return (
      <main className="flex flex-1 items-center justify-center px-4 py-12">
        <p aria-live="polite" className="text-zinc-500">
          회의록을 불러오는 중...
        </p>
      </main>
    );
  }

  if (loadError) {
    return (
      <main className="flex flex-1 items-center justify-center px-4 py-12">
        <div
          role="alert"
          className="w-full max-w-lg rounded-lg border border-red-200 bg-red-50 p-6 text-center sm:p-8 dark:border-red-900 dark:bg-red-950"
        >
          <p className="text-sm text-red-600 dark:text-red-400">
            {loadError}
          </p>
          <button
            type="button"
            onClick={handleRetry}
            className="mt-4 rounded-lg border border-red-300 px-3 py-1.5 text-sm text-red-600 transition-colors hover:bg-red-100 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-900"
          >
            다시 시도
          </button>
        </div>
      </main>
    );
  }

  if (minutesNotFound) {
    return (
      <main className="flex flex-1 items-center justify-center px-4 py-12">
        <div className="text-center">
          <h1 className="text-2xl font-bold">회의록을 찾을 수 없습니다.</h1>
          <p className="mt-2 text-sm text-zinc-500">
            삭제되었거나 잘못된 주소일 수 있습니다.
          </p>
          <Link
            href={`/projects/${id}`}
            className="mt-6 inline-block rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
          >
            프로젝트로 돌아가기
          </Link>
        </div>
      </main>
    );
  }

  if (isProjectDeleted) {
    return (
      <main className="flex flex-1 items-center justify-center px-4 py-12">
        <div className="text-center">
          <h1 className="text-2xl font-bold">편집할 수 없는 회의록입니다.</h1>
          <p className="mt-2 text-sm text-zinc-500">
            삭제된 프로젝트를 복원한 뒤 다시 시도해 주세요.
          </p>
          <Link
            href={`/projects/${id}/minutes/${minutesId}`}
            className="mt-6 inline-block rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
          >
            회의록으로 돌아가기
          </Link>
        </div>
      </main>
    );
  }

  return (
    <main className="flex flex-1 flex-col items-center px-4 py-8 sm:py-12">
      <form onSubmit={handleSave} className="w-full max-w-4xl">
        <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <h1 className="text-2xl font-bold">회의록 편집</h1>
          <div className="flex w-full gap-2 sm:w-auto">
            <button
              type="button"
              onClick={() =>
                router.push(`/projects/${id}/minutes/${minutesId}`)
              }
              disabled={isSaving}
              className="flex-1 rounded-lg border border-zinc-300 px-3 py-1.5 text-sm transition-colors hover:bg-zinc-100 disabled:cursor-not-allowed disabled:opacity-50 sm:flex-none dark:border-zinc-700 dark:hover:bg-zinc-800"
            >
              취소
            </button>
            <button
              type="submit"
              disabled={!title.trim() || !topic.trim() || isSaving}
              aria-busy={isSaving}
              className="flex-1 rounded-lg bg-zinc-900 px-4 py-1.5 text-sm font-medium text-white transition-colors hover:bg-zinc-800 disabled:cursor-not-allowed disabled:opacity-40 sm:flex-none dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
            >
              {isSaving ? "저장 중..." : "저장"}
            </button>
          </div>
        </div>

        {saveError && (
          <p
            role="alert"
            className="mb-6 rounded-lg bg-red-50 px-4 py-2.5 text-sm text-red-600 dark:bg-red-950 dark:text-red-400"
          >
            {saveError}
          </p>
        )}

        <fieldset disabled={isSaving} className="disabled:opacity-70">
          <section className="mb-6">
            <label htmlFor="minutes-title" className="mb-2 block text-lg font-semibold">
              제목
            </label>
            <input
              id="minutes-title"
              type="text"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              required
              className="w-full rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
            />
          </section>

          <section className="mb-6">
            <label htmlFor="minutes-topic" className="mb-2 block text-lg font-semibold">
              회의 주제
            </label>
            <input
              id="minutes-topic"
              type="text"
              value={topic}
              onChange={(event) => setTopic(event.target.value)}
              required
              className="w-full rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
            />
          </section>

          <EditableList
            title="주요 논의 내용"
            items={discussions}
            onChange={(index, value) =>
              updateItem(discussions, setDiscussions, index, value)
            }
            onAdd={() => addItem(discussions, setDiscussions)}
            onRemove={(index) =>
              removeItem(discussions, setDiscussions, index)
            }
          />

          <EditableList
            title="최종 결정 사항"
            items={decisions}
            onChange={(index, value) =>
              updateItem(decisions, setDecisions, index, value)
            }
            onAdd={() => addItem(decisions, setDecisions)}
            onRemove={(index) => removeItem(decisions, setDecisions, index)}
          />

          <EditableList
            title="미결정 사항"
            items={pending}
            onChange={(index, value) =>
              updateItem(pending, setPending, index, value)
            }
            onAdd={() => addItem(pending, setPending)}
            onRemove={(index) => removeItem(pending, setPending, index)}
          />

          <section className="mb-6">
            <div className="mb-2 flex items-center justify-between gap-4">
              <h2 className="text-lg font-semibold">담당자별 업무</h2>
              <button
                type="button"
                onClick={() =>
                  setTodos([
                    ...todos,
                    { name: "", task: "", deadline: "" },
                  ])
                }
                className="shrink-0 text-sm text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100"
              >
                + 추가
              </button>
            </div>
            <div className="space-y-3 sm:space-y-2">
              {todos.map((todo, index) => (
                <div
                  key={index}
                  className="grid grid-cols-2 gap-2 rounded-lg border border-zinc-200 p-3 sm:grid-cols-[6rem_1fr_7rem_auto] sm:border-0 sm:p-0 dark:border-zinc-700"
                >
                  <input
                    type="text"
                    aria-label={`업무 ${index + 1} 담당자`}
                    placeholder="담당자"
                    value={todo.name}
                    onChange={(event) =>
                      updateTodo(index, "name", event.target.value)
                    }
                    className="min-w-0 rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
                  />
                  <input
                    type="text"
                    aria-label={`업무 ${index + 1} 내용`}
                    placeholder="업무 내용"
                    value={todo.task}
                    onChange={(event) =>
                      updateTodo(index, "task", event.target.value)
                    }
                    className="min-w-0 rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
                  />
                  <input
                    type="text"
                    aria-label={`업무 ${index + 1} 마감일`}
                    placeholder="마감일"
                    value={todo.deadline}
                    onChange={(event) =>
                      updateTodo(index, "deadline", event.target.value)
                    }
                    className="min-w-0 rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
                  />
                  <button
                    type="button"
                    onClick={() =>
                      setTodos(todos.filter((_, itemIndex) => itemIndex !== index))
                    }
                    className="justify-self-end text-sm text-red-500 hover:text-red-700 sm:justify-self-auto"
                  >
                    삭제
                  </button>
                </div>
              ))}
              {todos.length === 0 && (
                <p className="rounded-lg bg-zinc-50 px-4 py-2.5 text-sm text-zinc-500 dark:bg-zinc-900">
                  등록된 업무가 없습니다.
                </p>
              )}
            </div>
          </section>

          <EditableList
            title="다음 회의에서 확인할 내용"
            items={nextAgenda}
            onChange={(index, value) =>
              updateItem(nextAgenda, setNextAgenda, index, value)
            }
            onAdd={() => addItem(nextAgenda, setNextAgenda)}
            onRemove={(index) =>
              removeItem(nextAgenda, setNextAgenda, index)
            }
          />
        </fieldset>
      </form>
    </main>
  );
}

function normalizeItems(items: string[]) {
  return items.map((item) => item.trim()).filter(Boolean);
}

function EditableList({
  title,
  items,
  onChange,
  onAdd,
  onRemove,
}: {
  title: string;
  items: string[];
  onChange: (index: number, value: string) => void;
  onAdd: () => void;
  onRemove: (index: number) => void;
}) {
  return (
    <section className="mb-6">
      <div className="mb-2 flex items-center justify-between gap-4">
        <h2 className="text-lg font-semibold">{title}</h2>
        <button
          type="button"
          onClick={onAdd}
          className="shrink-0 text-sm text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100"
        >
          + 추가
        </button>
      </div>
      <div className="space-y-2">
        {items.map((item, index) => (
          <div key={index} className="flex items-center gap-2">
            <input
              type="text"
              aria-label={`${title} ${index + 1}`}
              value={item}
              onChange={(event) => onChange(index, event.target.value)}
              className="min-w-0 flex-1 rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
            />
            <button
              type="button"
              onClick={() => onRemove(index)}
              className="shrink-0 text-sm text-red-500 hover:text-red-700"
            >
              삭제
            </button>
          </div>
        ))}
        {items.length === 0 && (
          <p className="rounded-lg bg-zinc-50 px-4 py-2.5 text-sm text-zinc-500 dark:bg-zinc-900">
            등록된 내용이 없습니다.
          </p>
        )}
      </div>
    </section>
  );
}
