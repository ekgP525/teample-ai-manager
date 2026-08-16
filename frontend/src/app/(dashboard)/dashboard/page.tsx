"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { getProjects } from "@/lib/api/projects";
import type { Project } from "@/types/minutes";

export default function DashboardPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  const loadProjects = useCallback(async () => {
    setIsLoading(true);
    setError("");

    try {
      setProjects(await getProjects());
    } catch (loadError) {
      setError(
        loadError instanceof Error
          ? loadError.message
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
        setError("");
      })
      .catch((loadError: unknown) => {
        if (!controller.signal.aborted) {
          setError(
            loadError instanceof Error
              ? loadError.message
              : "프로젝트를 불러오지 못했습니다."
          );
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });

    return () => controller.abort();
  }, []);

  const visibleProjects = projects.filter(
    (project) => project.status !== "DELETED"
  );
  const currentProjects = visibleProjects.filter(
    (project) => project.status !== "DISPOSED"
  );
  const endedProjects = visibleProjects.filter(
    (project) => project.status === "DISPOSED"
  );

  return (
    <main className="flex flex-1 flex-col items-center px-4 py-8 sm:py-12">
      <div className="w-full max-w-4xl">
        <div className="mb-8">
          <h1 className="text-2xl font-bold">대시보드</h1>
          <p className="mt-1 text-sm text-zinc-500">
            프로젝트를 선택한 뒤 해당 프로젝트의 사용자별 진행 상황을 확인하세요.
          </p>
        </div>

        {isLoading ? (
          <LoadingState />
        ) : error ? (
          <ErrorState message={error} onRetry={() => void loadProjects()} />
        ) : visibleProjects.length === 0 ? (
          <EmptyState message="대시보드를 확인할 프로젝트가 없습니다." />
        ) : (
          <div className="space-y-10">
            <ProjectSection
              title="진행 중인 프로젝트"
              projects={currentProjects}
              emptyMessage="진행 중인 프로젝트가 없습니다."
            />
            {endedProjects.length > 0 && (
              <ProjectSection title="종료된 프로젝트" projects={endedProjects} />
            )}
          </div>
        )}
      </div>
    </main>
  );
}

function ProjectSection({
  title,
  projects,
  emptyMessage,
}: {
  title: string;
  projects: Project[];
  emptyMessage?: string;
}) {
  return (
    <section>
      <div className="mb-3 flex items-center justify-between gap-3">
        <h2 className="text-lg font-semibold">{title}</h2>
        <span className="text-sm text-zinc-500">{projects.length}개</span>
      </div>
      {projects.length > 0 ? (
        <div className="grid gap-3 sm:grid-cols-2">
          {projects.map((project) => (
            <Link
              key={project.id}
              href={`/projects/${project.id}/dashboard`}
              className="rounded-xl border border-zinc-200 bg-white p-4 transition-colors hover:border-zinc-300 hover:bg-zinc-50 dark:border-zinc-700 dark:bg-zinc-950 dark:hover:border-zinc-600 dark:hover:bg-zinc-900"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <h3 className="break-words font-semibold">{project.name}</h3>
                  <p className="mt-1 text-sm text-zinc-500">
                    팀원 {project.members.length}명
                  </p>
                </div>
                <ProjectStatusBadge status={project.status} />
              </div>
              <p className="mt-4 truncate text-sm text-zinc-500">
                {project.members.join(", ") || "등록된 팀원 없음"}
              </p>
            </Link>
          ))}
        </div>
      ) : (
        <EmptyState message={emptyMessage || "프로젝트가 없습니다."} />
      )}
    </section>
  );
}

function ProjectStatusBadge({ status }: { status: Project["status"] }) {
  const labels: Record<Project["status"], string> = {
    ACTIVE: "진행 중",
    DISPOSAL_SCHEDULED: "종료 예정",
    DISPOSED: "종료됨",
    DELETED: "삭제됨",
  };
  const classes: Record<Project["status"], string> = {
    ACTIVE:
      "bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300",
    DISPOSAL_SCHEDULED:
      "bg-amber-50 text-amber-700 dark:bg-amber-950 dark:text-amber-300",
    DISPOSED:
      "bg-zinc-200 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300",
    DELETED:
      "bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300",
  };

  return (
    <span
      className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${classes[status]}`}
    >
      {labels[status]}
    </span>
  );
}

function LoadingState() {
  return (
    <div
      aria-live="polite"
      className="rounded-lg border border-zinc-200 p-8 text-center dark:border-zinc-700"
    >
      <p className="text-zinc-500">프로젝트를 불러오는 중...</p>
    </div>
  );
}

function EmptyState({ message }: { message: string }) {
  return (
    <div className="rounded-lg border border-zinc-200 p-8 text-center dark:border-zinc-700">
      <p className="text-sm text-zinc-500">{message}</p>
    </div>
  );
}

function ErrorState({
  message,
  onRetry,
}: {
  message: string;
  onRetry: () => void;
}) {
  return (
    <div
      role="alert"
      className="rounded-lg border border-red-200 bg-red-50 p-8 text-center dark:border-red-900 dark:bg-red-950"
    >
      <p className="text-sm text-red-600 dark:text-red-400">{message}</p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-4 rounded-lg border border-red-300 px-3 py-1.5 text-sm text-red-600 hover:bg-red-100 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-900"
      >
        다시 시도
      </button>
    </div>
  );
}
