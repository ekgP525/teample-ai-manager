"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import type { MinutesSummary, Project } from "@/types/minutes";

class ApiRequestError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

async function getErrorMessage(response: Response, fallback: string) {
  try {
    const data = (await response.json()) as { message?: string };
    return data.message || `${fallback} (${response.status})`;
  } catch {
    return `${fallback} (${response.status})`;
  }
}

async function fetchProjectData(projectId: string, signal?: AbortSignal) {
  const [projectResponse, minutesResponse] = await Promise.all([
    fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/projects/${projectId}`, {
      signal,
    }),
    fetch(
      `${process.env.NEXT_PUBLIC_API_URL}/api/projects/${projectId}/minutes`,
      { signal }
    ),
  ]);

  if (!projectResponse.ok) {
    throw new ApiRequestError(
      await getErrorMessage(projectResponse, "프로젝트를 불러오지 못했습니다."),
      projectResponse.status
    );
  }

  if (!minutesResponse.ok) {
    throw new ApiRequestError(
      await getErrorMessage(
        minutesResponse,
        "회의록 목록을 불러오지 못했습니다."
      ),
      minutesResponse.status
    );
  }

  return {
    project: (await projectResponse.json()) as Project,
    minutesList: (await minutesResponse.json()) as MinutesSummary[],
  };
}

export default function ProjectDetailPage() {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const [project, setProject] = useState<Project | null>(null);
  const [minutesList, setMinutesList] = useState<MinutesSummary[]>([]);
  const [loadedProjectId, setLoadedProjectId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [projectNotFound, setProjectNotFound] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState("");

  useEffect(() => {
    const controller = new AbortController();

    void fetchProjectData(id, controller.signal)
      .then((data) => {
        setProject(data.project);
        setMinutesList(data.minutesList);
        setLoadError("");
        setProjectNotFound(false);
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;

        setProject(null);
        setMinutesList([]);

        if (error instanceof ApiRequestError && error.status === 404) {
          setProjectNotFound(true);
          setLoadError("");
          return;
        }

        setProjectNotFound(false);
        setLoadError(
          error instanceof Error
            ? error.message
            : "프로젝트를 불러오지 못했습니다."
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoadedProjectId(id);
          setIsLoading(false);
        }
      });

    return () => controller.abort();
  }, [id]);

  const handleRetry = async () => {
    setIsLoading(true);
    setLoadError("");
    setProjectNotFound(false);

    try {
      const data = await fetchProjectData(id);
      setProject(data.project);
      setMinutesList(data.minutesList);
    } catch (error) {
      setProject(null);
      setMinutesList([]);

      if (error instanceof ApiRequestError && error.status === 404) {
        setProjectNotFound(true);
      } else {
        setLoadError(
          error instanceof Error
            ? error.message
            : "프로젝트를 불러오지 못했습니다."
        );
      }
    } finally {
      setLoadedProjectId(id);
      setIsLoading(false);
    }
  };

  const handleDelete = async () => {
    setIsDeleting(true);
    setDeleteError("");

    try {
      const response = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/api/projects/${id}`,
        { method: "DELETE" }
      );

      if (!response.ok) {
        throw new Error(
          await getErrorMessage(response, "프로젝트를 삭제하지 못했습니다.")
        );
      }

      router.push("/projects");
    } catch (error) {
      setDeleteError(
        error instanceof Error
          ? error.message
          : "프로젝트를 삭제하지 못했습니다."
      );
    } finally {
      setIsDeleting(false);
    }
  };

  const pageIsLoading = isLoading || loadedProjectId !== id;

  if (pageIsLoading) {
    return (
      <main className="flex flex-1 items-center justify-center px-4 py-12">
        <p aria-live="polite" className="text-zinc-500">
          프로젝트를 불러오는 중...
        </p>
      </main>
    );
  }

  if (loadError) {
    return (
      <main className="flex flex-1 items-center justify-center px-4 py-12">
        <div
          role="alert"
          className="w-full max-w-lg rounded-lg border border-red-200 bg-red-50 p-8 text-center dark:border-red-900 dark:bg-red-950"
        >
          <p className="text-sm text-red-600 dark:text-red-400">{loadError}</p>
          <button
            type="button"
            onClick={() => void handleRetry()}
            className="mt-4 rounded-lg border border-red-300 px-3 py-1.5 text-sm text-red-600 transition-colors hover:bg-red-100 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-900"
          >
            다시 시도
          </button>
        </div>
      </main>
    );
  }

  if (projectNotFound || !project) {
    return (
      <main className="flex flex-1 items-center justify-center px-4 py-12">
        <div className="text-center">
          <h1 className="text-2xl font-bold">프로젝트를 찾을 수 없습니다.</h1>
          <p className="mt-2 text-sm text-zinc-500">
            삭제되었거나 잘못된 주소일 수 있습니다.
          </p>
          <Link
            href="/projects"
            className="mt-6 inline-block rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
          >
            프로젝트 목록으로
          </Link>
        </div>
      </main>
    );
  }

  return (
    <main className="flex flex-1 flex-col items-center px-4 py-12">
      <div className="w-full max-w-2xl">
        <div className="mb-8 flex items-center justify-between gap-4">
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
                type="button"
                onClick={() => {
                  setConfirmDelete(true);
                  setDeleteError("");
                }}
                className="rounded-lg border border-red-300 px-3 py-2 text-sm text-red-500 transition-colors hover:bg-red-50 dark:border-red-800 dark:hover:bg-red-950"
              >
                삭제
              </button>
            ) : (
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => void handleDelete()}
                  disabled={isDeleting}
                  className="rounded-lg bg-red-600 px-3 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {isDeleting ? "삭제 중..." : "확인"}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setConfirmDelete(false);
                    setDeleteError("");
                  }}
                  disabled={isDeleting}
                  className="rounded-lg border border-zinc-300 px-3 py-2 text-sm hover:bg-zinc-100 disabled:cursor-not-allowed disabled:opacity-50 dark:border-zinc-700 dark:hover:bg-zinc-800"
                >
                  취소
                </button>
              </div>
            )}
          </div>
        </div>

        {deleteError && (
          <p
            role="alert"
            className="mb-6 rounded-lg bg-red-50 px-4 py-2.5 text-sm text-red-600 dark:bg-red-950 dark:text-red-400"
          >
            {deleteError}
          </p>
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
                      <p className="text-xs text-zinc-500">
                        {item.meetingDate}
                      </p>
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
