"use client";

import { useState, useEffect } from "react";
import { useRouter, useParams } from "next/navigation";
import Link from "next/link";
import type { Project, MinutesSummary } from "@/types/minutes";

export default function ProjectDetailPage() {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const [project, setProject] = useState<Project | null>(null);
  const [minutesList, setMinutesList] = useState<MinutesSummary[]>([]);
  const [meetingDate, setMeetingDate] = useState("");
  const [rawText, setRawText] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/projects/${id}`)
      .then((res) => res.json())
      .then(setProject)
      .catch(() => {});

    fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/projects/${id}/minutes`)
      .then((res) => res.json())
      .then(setMinutesList)
      .catch(() => {});
  }, [id]);

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
          body: JSON.stringify({ meetingDate, rawText }),
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
        {project && (
          <div className="mb-8">
            <h1 className="text-2xl font-bold">{project.name}</h1>
            <p className="text-sm text-zinc-500">
              팀원: {project.members.join(", ")}
            </p>
          </div>
        )}

        {/* 회의록 목록 */}
        {minutesList.length > 0 && (
          <section className="mb-10">
            <h2 className="mb-3 text-lg font-semibold">회의록 목록</h2>
            <div className="space-y-2">
              {minutesList.map((item) => (
                <Link
                  key={item.id}
                  href={`/projects/${id}/minutes/${item.id}`}
                  className="block rounded-lg border border-zinc-200 p-3 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:hover:bg-zinc-900"
                >
                  <div className="flex items-start justify-between">
                    <div>
                      <p className="font-medium">{item.topic}</p>
                      <p className="text-xs text-zinc-500">{item.meetingDate}</p>
                    </div>
                    <span className="text-xs text-zinc-400">
                      {item.createdAt?.slice(0, 10)}
                    </span>
                  </div>
                </Link>
              ))}
            </div>
          </section>
        )}

        {/* 새 회의록 생성 폼 */}
        <section>
          <h2 className="mb-2 text-lg font-semibold">새 회의록 생성</h2>
          <p className="text-zinc-500 mb-4 text-sm">
            카톡 대화를 붙여넣으면 AI가 회의록을 자동 생성합니다.
          </p>

          <form onSubmit={handleSubmit} className="flex flex-col gap-5">
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

            <button
              type="submit"
              disabled={!meetingDate || !rawText || isLoading}
              className="rounded-lg bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-zinc-800 disabled:cursor-not-allowed disabled:opacity-40 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
            >
              {isLoading ? "회의록 생성 중..." : "회의록 생성하기"}
            </button>
          </form>
        </section>
      </div>
    </main>
  );
}
