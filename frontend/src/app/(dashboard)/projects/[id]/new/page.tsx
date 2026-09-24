"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter, useParams } from "next/navigation";
import { ApiError } from "@/lib/api/client";
import { createMinutes } from "@/lib/api/minutes";
import { getProject } from "@/lib/api/projects";
import type { Project } from "@/types/minutes";

export default function NewMinutesPage() {
  const router = useRouter();
  const generationRequest = useRef<{ payload: string; key: string } | null>(null);
  const { id } = useParams<{ id: string }>();
  const [title, setTitle] = useState("");
  const [meetingDate, setMeetingDate] = useState("");
  const [rawText, setRawText] = useState("");
  const [projectStatus, setProjectStatus] =
    useState<Project["status"] | null>(null);
  const [loadedProjectId, setLoadedProjectId] = useState("");
  const [isCheckingProject, setIsCheckingProject] = useState(true);
  const [projectError, setProjectError] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");

  const loadProject = useCallback(
    async (signal?: AbortSignal) => {
      try {
        const project = await getProject(id, signal);
        setProjectStatus(project.status);
        setProjectError("");
      } catch (loadError) {
        if (signal?.aborted) return;

        setProjectStatus(null);
        setProjectError(
          loadError instanceof Error
            ? loadError.message
            : "프로젝트를 확인하지 못했습니다."
        );
      } finally {
        if (!signal?.aborted) {
          setLoadedProjectId(id);
          setIsCheckingProject(false);
        }
      }
    },
    [id]
  );

  useEffect(() => {
    const controller = new AbortController();

    void getProject(id, controller.signal)
      .then((project) => {
        setProjectStatus(project.status);
        setProjectError("");
      })
      .catch((loadError: unknown) => {
        if (controller.signal.aborted) return;

        setProjectStatus(null);
        setProjectError(
          loadError instanceof Error
            ? loadError.message
            : "프로젝트를 확인하지 못했습니다."
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoadedProjectId(id);
          setIsCheckingProject(false);
        }
      });

    return () => controller.abort();
  }, [id]);

  const handleProjectRetry = () => {
    setIsCheckingProject(true);
    setLoadedProjectId("");
    setProjectError("");
    void loadProject();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (
      isLoading ||
      (projectStatus !== "ACTIVE" && projectStatus !== "DISPOSAL_SCHEDULED")
    ) {
      return;
    }

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
      const input = { title: title.trim(), meetingDate, rawText: trimmedRawText };
      const payload = JSON.stringify({ id, ...input });
      if (generationRequest.current?.payload !== payload) {
        generationRequest.current = { payload, key: crypto.randomUUID() };
      }
      const data = await createMinutes(id, input, generationRequest.current.key);
      router.push(`/projects/${id}/minutes/${data.id}`);
    } catch (err) {
      if (err instanceof ApiError && [400, 410, 429, 502, 503].includes(err.status)) generationRequest.current = null;
      setError(
        err instanceof Error ? err.message : "회의록 생성에 실패했습니다."
      );
    } finally {
      setIsLoading(false);
    }
  };

  if (isCheckingProject || loadedProjectId !== id) {
    return (
      <main className="flex flex-1 items-center justify-center px-4 py-12">
        <p aria-live="polite" className="text-zinc-500">
          프로젝트를 확인하는 중...
        </p>
      </main>
    );
  }

  if (projectError) {
    return (
      <main className="flex flex-1 items-center justify-center px-4 py-12">
        <div
          role="alert"
          className="w-full max-w-lg rounded-lg border border-red-200 bg-red-50 p-6 text-center dark:border-red-900 dark:bg-red-950"
        >
          <p className="text-sm text-red-600 dark:text-red-400">
            {projectError}
          </p>
          <button
            type="button"
            onClick={handleProjectRetry}
            className="mt-4 rounded-lg border border-red-300 px-3 py-1.5 text-sm text-red-600 transition-colors hover:bg-red-100 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-900"
          >
            다시 시도
          </button>
        </div>
      </main>
    );
  }

  if (projectStatus === "DISPOSED" || projectStatus === "DELETED") {
    const isDeleted = projectStatus === "DELETED";

    return (
      <main className="flex flex-1 items-center justify-center px-4 py-12">
        <div className="text-center">
          <h1 className="text-2xl font-bold">새 회의록을 만들 수 없습니다.</h1>
          <p className="mt-2 text-sm text-zinc-500">
            {isDeleted
              ? "삭제된 프로젝트를 복원한 뒤 다시 시도해 주세요."
              : "종료된 프로젝트에는 새 회의록을 추가할 수 없습니다."}
          </p>
          <Link
            href={`/projects/${id}`}
            className="mt-6 inline-block rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
          >
            프로젝트로 돌아가기
          </Link>
        </div>
      </main>
    );
  }

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
              maxLength={255}
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
              maxLength={50000}
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
