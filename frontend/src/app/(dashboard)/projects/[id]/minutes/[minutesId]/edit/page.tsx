"use client";

import { useState, useEffect } from "react";
import { useRouter, useParams } from "next/navigation";
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
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/projects/${id}/minutes/${minutesId}`)
      .then((res) => {
        if (!res.ok) throw new Error("회의록을 불러올 수 없습니다.");
        return res.json();
      })
      .then((data: Minutes) => {
        setTitle(data.title);
        setTopic(data.topic);
        setDiscussions(data.discussions);
        setDecisions(data.decisions);
        setPending(data.pending);
        setTodos(data.todos);
        setNextAgenda(data.nextAgenda);
      })
      .catch((err) => setError(err.message))
      .finally(() => setIsLoading(false));
  }, [minutesId]);

  const handleSave = async () => {
    setIsSaving(true);
    setError("");

    try {
      const res = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/api/projects/${id}/minutes/${minutesId}`,
        {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            id: minutesId,
            title,
            topic,
            discussions,
            decisions,
            pending,
            todos,
            nextAgenda,
          }),
        }
      );

      if (!res.ok) throw new Error(`저장 실패 (${res.status})`);
      router.push(`/projects/${id}/minutes/${minutesId}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "저장에 실패했습니다.");
    } finally {
      setIsSaving(false);
    }
  };

  // 문자열 배열 항목 수정
  const updateItem = (
    list: string[],
    setList: (v: string[]) => void,
    index: number,
    value: string
  ) => {
    const next = [...list];
    next[index] = value;
    setList(next);
  };

  const addItem = (list: string[], setList: (v: string[]) => void) => {
    setList([...list, ""]);
  };

  const removeItem = (
    list: string[],
    setList: (v: string[]) => void,
    index: number
  ) => {
    setList(list.filter((_, i) => i !== index));
  };

  // Todo 항목 수정
  const updateTodo = (index: number, field: keyof Todo, value: string) => {
    const next = [...todos];
    next[index] = { ...next[index], [field]: value };
    setTodos(next);
  };

  const addTodo = () => {
    setTodos([...todos, { name: "", task: "", deadline: "" }]);
  };

  const removeTodo = (index: number) => {
    setTodos(todos.filter((_, i) => i !== index));
  };

  if (isLoading) {
    return (
      <main className="flex flex-1 items-center justify-center">
        <p className="text-zinc-500">불러오는 중...</p>
      </main>
    );
  }

  return (
    <main className="flex flex-1 flex-col items-center px-4 py-12">
      <div className="w-full max-w-2xl">
        <div className="mb-8 flex items-center justify-between">
          <h1 className="text-2xl font-bold">회의록 편집</h1>
          <div className="flex gap-2">
            <button
              onClick={() =>
                router.push(`/projects/${id}/minutes/${minutesId}`)
              }
              className="rounded-lg border border-zinc-300 px-3 py-1.5 text-sm transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
            >
              취소
            </button>
            <button
              onClick={handleSave}
              disabled={isSaving}
              className="rounded-lg bg-zinc-900 px-4 py-1.5 text-sm font-medium text-white transition-colors hover:bg-zinc-800 disabled:opacity-40 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
            >
              {isSaving ? "저장 중..." : "저장"}
            </button>
          </div>
        </div>

        {error && (
          <p className="mb-6 rounded-lg bg-red-50 px-4 py-2.5 text-sm text-red-600 dark:bg-red-950 dark:text-red-400">
            {error}
          </p>
        )}

        {/* 제목 */}
        <section className="mb-6">
          <label className="mb-2 block text-lg font-semibold">제목</label>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="w-full rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
          />
        </section>

        {/* 회의 주제 */}
        <section className="mb-6">
          <label className="mb-2 block text-lg font-semibold">회의 주제</label>
          <input
            type="text"
            value={topic}
            onChange={(e) => setTopic(e.target.value)}
            className="w-full rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
          />
        </section>

        {/* 주요 논의 내용 */}
        <EditableList
          title="주요 논의 내용"
          items={discussions}
          onChange={(i, v) => updateItem(discussions, setDiscussions, i, v)}
          onAdd={() => addItem(discussions, setDiscussions)}
          onRemove={(i) => removeItem(discussions, setDiscussions, i)}
        />

        {/* 최종 결정 사항 */}
        <EditableList
          title="최종 결정 사항"
          items={decisions}
          onChange={(i, v) => updateItem(decisions, setDecisions, i, v)}
          onAdd={() => addItem(decisions, setDecisions)}
          onRemove={(i) => removeItem(decisions, setDecisions, i)}
        />

        {/* 미결정 사항 */}
        <EditableList
          title="미결정 사항"
          items={pending}
          onChange={(i, v) => updateItem(pending, setPending, i, v)}
          onAdd={() => addItem(pending, setPending)}
          onRemove={(i) => removeItem(pending, setPending, i)}
        />

        {/* 담당자별 업무 */}
        <section className="mb-6">
          <div className="mb-2 flex items-center justify-between">
            <h2 className="text-lg font-semibold">담당자별 업무</h2>
            <button
              onClick={addTodo}
              className="text-sm text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100"
            >
              + 추가
            </button>
          </div>
          <div className="space-y-2">
            {todos.map((todo, i) => (
              <div key={i} className="flex gap-2">
                <input
                  type="text"
                  placeholder="담당자"
                  value={todo.name}
                  onChange={(e) => updateTodo(i, "name", e.target.value)}
                  className="w-24 rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
                />
                <input
                  type="text"
                  placeholder="업무 내용"
                  value={todo.task}
                  onChange={(e) => updateTodo(i, "task", e.target.value)}
                  className="flex-1 rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
                />
                <input
                  type="text"
                  placeholder="마감일"
                  value={todo.deadline}
                  onChange={(e) => updateTodo(i, "deadline", e.target.value)}
                  className="w-28 rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
                />
                <button
                  onClick={() => removeTodo(i)}
                  className="text-sm text-red-500 hover:text-red-700"
                >
                  삭제
                </button>
              </div>
            ))}
          </div>
        </section>

        {/* 다음 회의에서 확인할 내용 */}
        <EditableList
          title="다음 회의에서 확인할 내용"
          items={nextAgenda}
          onChange={(i, v) => updateItem(nextAgenda, setNextAgenda, i, v)}
          onAdd={() => addItem(nextAgenda, setNextAgenda)}
          onRemove={(i) => removeItem(nextAgenda, setNextAgenda, i)}
        />
      </div>
    </main>
  );
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
      <div className="mb-2 flex items-center justify-between">
        <h2 className="text-lg font-semibold">{title}</h2>
        <button
          onClick={onAdd}
          className="text-sm text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100"
        >
          + 추가
        </button>
      </div>
      <div className="space-y-2">
        {items.map((item, i) => (
          <div key={i} className="flex gap-2">
            <input
              type="text"
              value={item}
              onChange={(e) => onChange(i, e.target.value)}
              className="flex-1 rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
            />
            <button
              onClick={() => onRemove(i)}
              className="text-sm text-red-500 hover:text-red-700"
            >
              삭제
            </button>
          </div>
        ))}
      </div>
    </section>
  );
}
