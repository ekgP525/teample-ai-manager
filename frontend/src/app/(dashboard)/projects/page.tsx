"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { createProject, getProjects } from "@/lib/api/projects";
import type { Project } from "@/types/minutes";
import { ProjectStatusBadge } from "@/components/project-status-badge";

export default function ProjectsPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [isCreating, setIsCreating] = useState(false);
  const [name, setName] = useState("");
  const [endDate, setEndDate] = useState("");
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
    const controller = new AbortController();

    void getProjects(controller.signal)
      .then((data) => {
        setProjects(data);
        setLoadError("");
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setLoadError(
            error instanceof Error
              ? error.message
              : "프로젝트를 불러오지 못했습니다."
          );
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });

    return () => controller.abort();
  }, []);

  const handleCreate = async (event: React.FormEvent) => {
    event.preventDefault();
    setIsSubmitting(true);
    setCreateError("");

    try {
      const created = await createProject({
        name: name.trim(),
        endDate: endDate || undefined,
      });
      setProjects((currentProjects) => [created, ...currentProjects]);
      setName("");
      setEndDate("");
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

  const currentProjects = projects.filter(
    (project) =>
      project.status !== "DISPOSED" && project.status !== "DELETED"
  );
  const endedProjects = projects.filter(
    (project) => project.status === "DISPOSED"
  );
  const hasAnyProjects = projects.length > 0;

  return (
    <main className="flex flex-1 flex-col items-center px-4 py-8 sm:py-12">
      <div className="w-full max-w-2xl">
        <div className="mb-8 flex items-start justify-between gap-4">
          <h1 className="text-2xl font-bold">
            {isCreating ? "새 프로젝트" : "프로젝트"}
          </h1>
          <div className="flex shrink-0 flex-wrap justify-end gap-2">
            {!isCreating && (
              <Link
                href="/join"
                className="rounded-lg border border-zinc-300 px-3 py-1.5 text-sm font-medium transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-900"
              >
                초대 코드 입력
              </Link>
            )}
            <button
              type="button"
              onClick={() => {
                setIsCreating((current) => !current);
                setCreateError("");
              }}
              className="shrink-0 cursor-pointer rounded-lg bg-zinc-900 px-3 py-1.5 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
            >
              {isCreating ? "취소" : "새 프로젝트"}
            </button>
          </div>
        </div>

        {isCreating && (
          <form
            onSubmit={handleCreate}
            className="mb-8 rounded-lg border border-zinc-200 p-4 dark:border-zinc-700"
          >
            <div className="flex flex-col gap-4">
              <div className="flex flex-col gap-1.5">
                <label htmlFor="project-name" className="text-sm font-medium">
                  프로젝트명 (과목명)
                </label>
                <input
                  id="project-name"
                  type="text"
                  placeholder="예: AI캡스톤디자인"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  disabled={isSubmitting}
                  className="rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <label htmlFor="end-date" className="text-sm font-medium">
                  프로젝트 종료 예정일 (선택)
                </label>
                <input
                  id="end-date"
                  type="date"
                  value={endDate}
                  onChange={(event) => setEndDate(event.target.value)}
                  disabled={isSubmitting}
                  className="rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
                />
                <p className="text-xs text-zinc-500">
                  날짜가 지나면 프로젝트가 자동으로 종료 상태로 전환됩니다.
                </p>
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
                disabled={!name.trim() || isSubmitting}
                className="rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-800 disabled:opacity-40 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
              >
                {isSubmitting ? "생성 중..." : "생성"}
              </button>
            </div>
          </form>
        )}

        {!isCreating &&
          (isLoading ? (
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
          ) : !hasAnyProjects ? (
            <div className="rounded-lg border border-zinc-200 p-8 text-center dark:border-zinc-700">
              <p className="text-zinc-500">
                아직 프로젝트가 없습니다. 새 프로젝트를 만들어보세요.
              </p>
            </div>
          ) : (
            <div className="space-y-8">
              <section aria-labelledby="current-projects-heading">
                <h2
                  id="current-projects-heading"
                  className="mb-3 text-lg font-semibold"
                >
                  진행 중인 프로젝트
                </h2>
                {currentProjects.length > 0 ? (
                  <div className="space-y-3">
                    {currentProjects.map((project) => (
                      <ProjectCard key={project.id} project={project} />
                    ))}
                  </div>
                ) : (
                  <p className="rounded-lg border border-zinc-200 p-6 text-center text-sm text-zinc-500 dark:border-zinc-700">
                    진행 중인 프로젝트가 없습니다.
                  </p>
                )}
              </section>

              {endedProjects.length > 0 && (
                <section aria-labelledby="ended-projects-heading">
                  <div className="mb-3 flex items-center justify-between gap-3">
                    <h2
                      id="ended-projects-heading"
                      className="text-lg font-semibold"
                    >
                      종료된 프로젝트
                    </h2>
                    <span className="text-sm text-zinc-500">
                      {endedProjects.length}개
                    </span>
                  </div>
                  <div className="space-y-3">
                    {endedProjects.map((project) => (
                      <ProjectCard key={project.id} project={project} />
                    ))}
                  </div>
                </section>
              )}
            </div>
          ))}
      </div>
    </main>
  );
}

function ProjectCard({ project }: { project: Project }) {
  const endDate = project.endDate ?? project.disposalDeadline;

  return (
    <Link
      href={`/projects/${project.id}`}
      className="block break-words rounded-lg border border-zinc-200 p-4 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:hover:bg-zinc-900"
    >
      <div className="flex items-start justify-between gap-3">
        <h3 className="font-semibold">{project.name}</h3>
        <ProjectStatusBadge project={project} />
      </div>
      <p className="mt-1 text-sm text-zinc-500">
        {project.members.join(", ")}
      </p>
      {endDate && (
        <p className="mt-1 text-xs text-zinc-400">종료 예정일 {endDate}</p>
      )}
    </Link>
  );
}
