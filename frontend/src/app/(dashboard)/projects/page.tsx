"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { createProject, getProjects } from "@/lib/api/projects";
import type { Project } from "@/types/minutes";

export default function ProjectsPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [isCreating, setIsCreating] = useState(false);
  const [name, setName] = useState("");
  const [members, setMembers] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [createError, setCreateError] = useState("");

  const loadProjects = useCallback(async () => {
    setIsLoading(true);
    setLoadError("");

    try {
      const data = await getProjects();
      setProjects(data);
    } catch (error) {
      setLoadError(
        error instanceof Error
          ? error.message
          : "프로젝트를 불러오지 못했습니다."
      );
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    let isCancelled = false;

    void getProjects()
      .then((data) => {
        if (!isCancelled) setProjects(data);
      })
      .catch((error: unknown) => {
        if (!isCancelled) {
          setLoadError(
            error instanceof Error
              ? error.message
              : "프로젝트를 불러오지 못했습니다."
          );
        }
      })
      .finally(() => {
        if (!isCancelled) setIsLoading(false);
      });

    return () => {
      isCancelled = true;
    };
  }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setCreateError("");

    try {
      const created = await createProject({
        name: name.trim(),
        members: members
          .split(",")
          .map((member) => member.trim())
          .filter(Boolean),
      });
      setProjects((currentProjects) => [created, ...currentProjects]);
      setName("");
      setMembers("");
      setIsCreating(false);
    } catch (error) {
      setCreateError(
        error instanceof Error
          ? error.message
          : "프로젝트를 생성하지 못했습니다."
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  const memberList = members
    .split(",")
    .map((member) => member.trim())
    .filter(Boolean);

  return (
    <main className="flex flex-1 flex-col items-center px-4 py-12">
      <div className="w-full max-w-2xl">
        <div className="mb-8 flex items-center justify-between">
          <h1 className="text-2xl font-bold">프로젝트</h1>
          <button
            onClick={() => {
              setIsCreating((current) => !current);
              setCreateError("");
            }}
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
                  disabled={isSubmitting}
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
                  disabled={isSubmitting}
                  className="rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
                />
              </div>
              {createError && (
                <p
                  role="alert"
                  className="rounded-lg bg-red-50 px-4 py-2.5 text-sm text-red-600 dark:bg-red-950 dark:text-red-400"
                >
                  {createError}
                </p>
              )}
              <button
                type="submit"
                disabled={!name.trim() || memberList.length === 0 || isSubmitting}
                className="rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-800 disabled:opacity-40 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
              >
                {isSubmitting ? "생성 중..." : "생성"}
              </button>
            </div>
          </form>
        )}

        {isLoading ? (
          <div
            aria-live="polite"
            className="rounded-lg border border-zinc-200 p-8 text-center dark:border-zinc-700"
          >
            <p className="text-zinc-500">프로젝트를 불러오는 중...</p>
          </div>
        ) : loadError ? (
          <div
            role="alert"
            className="rounded-lg border border-red-200 bg-red-50 p-8 text-center dark:border-red-900 dark:bg-red-950"
          >
            <p className="text-sm text-red-600 dark:text-red-400">
              {loadError}
            </p>
            <button
              type="button"
              onClick={() => void loadProjects()}
              className="mt-4 rounded-lg border border-red-300 px-3 py-1.5 text-sm text-red-600 transition-colors hover:bg-red-100 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-900"
            >
              다시 시도
            </button>
          </div>
        ) : projects.length === 0 ? (
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
