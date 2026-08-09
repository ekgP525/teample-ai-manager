"use client";

import { useState } from "react";
import { useRouter, useParams } from "next/navigation";
import { createMinutes } from "@/lib/api/minutes";

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

    if (isLoading) return;

    const trimmedRawText = rawText.trim();
    if (!meetingDate) {
      setError("회의 날짜를 선택해주세요.");
      return;
    }

    if (!trimmedRawText) {
      setError("카카오톡 대화 내용을 입력해주세요.");
      return;
    }

    setIsLoading(true);
    setError("");

    try {
      const data = await createMinutes(id, {
        title: title.trim(),
        meetingDate,
        rawText: trimmedRawText,
      });
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
    <main className="flex flex-1 flex-col items-center px-4 py-8 sm:py-12">
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
              disabled={isLoading}
              aria-describedby="title-help"
              className="rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
            />
            <p id="title-help" className="text-xs text-zinc-500">
              입력하지 않으면 대화 내용을 바탕으로 AI가 제목을 만듭니다.
            </p>
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
              required
              disabled={isLoading}
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
              required
              disabled={isLoading}
              aria-describedby="raw-text-count"
              className="rounded-lg border border-zinc-300 px-3 py-2 text-sm leading-relaxed focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
            />
            <p
              id="raw-text-count"
              className="text-right text-xs text-zinc-500"
            >
              {rawText.length.toLocaleString()}자
            </p>
          </div>

          {error && (
            <p
              role="alert"
              className="rounded-lg bg-red-50 px-4 py-2.5 text-sm text-red-600 dark:bg-red-950 dark:text-red-400"
            >
              {error}
            </p>
          )}

          <div className="flex flex-col-reverse gap-2 sm:flex-row">
            <button
              type="button"
              onClick={() => router.push(`/projects/${id}`)}
              disabled={isLoading}
              className="rounded-lg border border-zinc-300 px-4 py-2.5 text-sm transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
            >
              취소
            </button>
            <button
              type="submit"
              disabled={!meetingDate || !rawText.trim() || isLoading}
              aria-busy={isLoading}
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
