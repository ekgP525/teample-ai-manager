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
  const [confirmDelete, setConfirmDelete] = useState(false);

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

  return (
    <main className="flex flex-1 flex-col items-center px-4 py-12">
      <div className="w-full max-w-2xl">
        {project && (
          <div className="mb-8 flex items-center justify-between">
            <div>
              <h1 className="text-2xl font-bold">{project.name}</h1>
              <p className="text-sm text-zinc-500">
                팀원: {project.members.join(", ")}
              </p>
            </div>
            <div className="flex gap-2">
              <Link
                href={`/projects/${id}/new`}
                className="rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
              >
                새 회의록
              </Link>
              {!confirmDelete ? (
                <button
                  onClick={() => setConfirmDelete(true)}
                  className="rounded-lg border border-red-300 px-3 py-2 text-sm text-red-500 transition-colors hover:bg-red-50 dark:border-red-800 dark:hover:bg-red-950"
                >
                  삭제
                </button>
              ) : (
                <div className="flex items-center gap-2">
                  <button
                    onClick={async () => {
                      const res = await fetch(
                        `${process.env.NEXT_PUBLIC_API_URL}/api/projects/${id}`,
                        { method: "DELETE" }
                      );
                      if (res.ok) router.push("/projects");
                    }}
                    className="rounded-lg bg-red-600 px-3 py-2 text-sm font-medium text-white hover:bg-red-700"
                  >
                    확인
                  </button>
                  <button
                    onClick={() => setConfirmDelete(false)}
                    className="rounded-lg border border-zinc-300 px-3 py-2 text-sm hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
                  >
                    취소
                  </button>
                </div>
              )}
            </div>
          </div>
        )}

        {minutesList.length > 0 ? (
          <section>
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
                      <p className="font-medium">{item.title || item.topic}</p>
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
        ) : (
          <div className="rounded-lg border border-zinc-200 p-8 text-center dark:border-zinc-700">
            <p className="text-zinc-500">
              아직 회의록이 없습니다. 새 회의록을 만들어보세요.
            </p>
          </div>
        )}
      </div>
    </main>
  );
}
