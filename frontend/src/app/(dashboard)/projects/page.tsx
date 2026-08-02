"use client";

import Link from "next/link";
import { useState, useEffect } from "react";
import type { Project } from "@/types/minutes";

export default function ProjectsPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [isCreating, setIsCreating] = useState(false);
  const [name, setName] = useState("");
  const [members, setMembers] = useState("");

  useEffect(() => {
    fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/projects`)
      .then((res) => res.json())
      .then(setProjects)
      .catch(() => {});
  }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    const res = await fetch(
      `${process.env.NEXT_PUBLIC_API_URL}/api/projects`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name,
          members: members.split(",").map((m) => m.trim()).filter(Boolean),
        }),
      }
    );
    if (res.ok) {
      const created = await res.json();
      setProjects([created, ...projects]);
      setName("");
      setMembers("");
      setIsCreating(false);
    }
  };

  return (
    <main className="flex flex-1 flex-col items-center px-4 py-12">
      <div className="w-full max-w-2xl">
        <div className="mb-8 flex items-center justify-between">
          <h1 className="text-2xl font-bold">프로젝트</h1>
          <button
            onClick={() => setIsCreating(!isCreating)}
            className="rounded-lg bg-zinc-900 px-3 py-1.5 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
          >
            {isCreating ? "취소" : "새 프로젝트"}
          </button>
        </div>

        {isCreating && (
          <form
            onSubmit={handleCreate}
            className="mb-8 rounded-lg border border-zinc-200 p-4 dark:border-zinc-700"
          >
            <div className="flex flex-col gap-4">
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium">프로젝트명 (과목명)</label>
                <input
                  type="text"
                  placeholder="예: AI캡스톤디자인"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium">
                  팀원 이름 (쉼표로 구분)
                </label>
                <input
                  type="text"
                  placeholder="예: 이다혜, 박규남, 김다희"
                  value={members}
                  onChange={(e) => setMembers(e.target.value)}
                  className="rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
                />
              </div>
              <button
                type="submit"
                disabled={!name || !members}
                className="rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-800 disabled:opacity-40 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
              >
                생성
              </button>
            </div>
          </form>
        )}

        {projects.length === 0 ? (
          <div className="rounded-lg border border-zinc-200 p-8 text-center dark:border-zinc-700">
            <p className="text-zinc-500">
              아직 프로젝트가 없습니다. 새 프로젝트를 만들어보세요.
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {projects.map((project) => (
              <Link
                key={project.id}
                href={`/projects/${project.id}`}
                className="block rounded-lg border border-zinc-200 p-4 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:hover:bg-zinc-900"
              >
                <h2 className="font-semibold">{project.name}</h2>
                <p className="mt-1 text-sm text-zinc-500">
                  {project.members.join(", ")}
                </p>
              </Link>
            ))}
          </div>
        )}
      </div>
    </main>
  );
}
