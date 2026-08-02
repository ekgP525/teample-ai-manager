"use client";

import { useState } from "react";
import { useRouter, useParams } from "next/navigation";

export default function NewMinutesPage() {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const [title, setTitle] = useState("");
  const [meetingDate, setMeetingDate] = useState("");
  const [rawText, setRawText] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError("");

    try {
      const res = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/api/projects/${id}/minutes`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ title, meetingDate, rawText }),
        }
      );

      if (!res.ok) throw new Error(`서버 오류 (${res.status})`);

      const data = await res.json();
      router.push(`/projects/${id}/minutes/${data.id}`);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "회의록 생성에 실패했습니다."
      );
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <main className="flex flex-1 flex-col items-center px-4 py-12">
      <div className="w-full max-w-2xl">
        <div className="mb-8">
          <h1 className="text-2xl font-bold">새 회의록 생성</h1>
          <p className="mt-1 text-sm text-zinc-500">
            카톡 대화를 붙여넣으면 AI가 회의록을 자동 생성합니다.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="title" className="text-sm font-medium">
              회의록 제목
            </label>
            <input
              id="title"
              type="text"
              placeholder="예: 1차 기획 회의 (비워두면 AI가 자동 생성)"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="meetingDate" className="text-sm font-medium">
              회의 날짜
            </label>
            <input
              id="meetingDate"
              type="date"
              value={meetingDate}
              onChange={(e) => setMeetingDate(e.target.value)}
              className="rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="rawText" className="text-sm font-medium">
              카톡 대화 내용
            </label>
            <textarea
              id="rawText"
              rows={12}
              placeholder="카카오톡 단톡방 대화를 복사해서 여기에 붙여넣으세요..."
              value={rawText}
              onChange={(e) => setRawText(e.target.value)}
              className="rounded-lg border border-zinc-300 px-3 py-2 text-sm leading-relaxed focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
            />
          </div>

          {error && (
            <p className="rounded-lg bg-red-50 px-4 py-2.5 text-sm text-red-600 dark:bg-red-950 dark:text-red-400">
              {error}
            </p>
          )}

          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => router.push(`/projects/${id}`)}
              className="rounded-lg border border-zinc-300 px-4 py-2.5 text-sm transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
            >
              취소
            </button>
            <button
              type="submit"
              disabled={!meetingDate || !rawText || isLoading}
              className="flex-1 rounded-lg bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-zinc-800 disabled:cursor-not-allowed disabled:opacity-40 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
            >
              {isLoading ? "회의록 생성 중..." : "회의록 생성하기"}
            </button>
          </div>
        </form>
      </div>
    </main>
  );
}
