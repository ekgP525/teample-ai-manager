"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  getDeletedProjects,
  permanentlyDeleteProject,
  restoreProject,
} from "@/lib/api/projects";
import type { Project } from "@/types/minutes";

type TrashAction = "restore" | "permanent" | "";

export default function TrashPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [actionError, setActionError] = useState("");
  const [pendingProjectId, setPendingProjectId] = useState("");
  const [pendingAction, setPendingAction] = useState<TrashAction>("");
  const [confirmPermanentId, setConfirmPermanentId] = useState("");

  const loadProjects = useCallback(async () => {
    setIsLoading(true);
    setLoadError("");

    try {
      setProjects(await getDeletedProjects());
    } catch (error) {
      setLoadError(
        error instanceof Error
          ? error.message
          : "휴지통을 불러오지 못했습니다."
      );
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();

    void getDeletedProjects(controller.signal)
      .then((data) => {
        setProjects(data);
        setLoadError("");
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setLoadError(
            error instanceof Error
              ? error.message
              : "휴지통을 불러오지 못했습니다."
          );
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });

    return () => controller.abort();
  }, []);

  const handleRestore = async (projectId: string) => {
    if (pendingProjectId) return;

    setPendingProjectId(projectId);
    setPendingAction("restore");
    setActionError("");

    try {
      await restoreProject(projectId);
      setProjects((current) =>
        current.filter((project) => project.id !== projectId)
      );
      setConfirmPermanentId("");
    } catch (error) {
      setActionError(
        error instanceof Error
          ? error.message
          : "프로젝트를 복원하지 못했습니다."
      );
    } finally {
      setPendingProjectId("");
      setPendingAction("");
    }
  };

  const handlePermanentDelete = async (projectId: string) => {
    if (pendingProjectId) return;

    setPendingProjectId(projectId);
    setPendingAction("permanent");
    setActionError("");

    try {
      await permanentlyDeleteProject(projectId);
      setProjects((current) =>
        current.filter((project) => project.id !== projectId)
      );
      setConfirmPermanentId("");
    } catch (error) {
      setActionError(
        error instanceof Error
          ? error.message
          : "프로젝트를 영구 삭제하지 못했습니다."
      );
    } finally {
      setPendingProjectId("");
      setPendingAction("");
    }
  };

  return (
    <main className="flex flex-1 flex-col items-center px-4 py-8 sm:py-12">
      <div className="w-full max-w-2xl">
        <div className="mb-8 flex items-end justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold">휴지통</h1>
            <p className="mt-1 text-sm text-zinc-500">
              삭제된 프로젝트를 복원하거나 영구 삭제할 수 있습니다.
            </p>
          </div>
          {!isLoading && !loadError && projects.length > 0 && (
            <span className="shrink-0 text-sm text-zinc-500">
              {projects.length}개
            </span>
          )}
        </div>

        {isLoading ? (
          <div
            aria-live="polite"
            className="rounded-lg border border-zinc-200 p-8 text-center dark:border-zinc-700"
          >
            <p className="text-zinc-500">휴지통을 불러오는 중...</p>
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
          <div className="rounded-lg border border-zinc-200 p-10 text-center dark:border-zinc-700">
            <TrashIcon className="mx-auto h-8 w-8 text-zinc-400" />
            <p className="mt-4 font-medium">휴지통이 비어 있습니다.</p>
            <p className="mt-1 text-sm text-zinc-500">
              삭제한 프로젝트가 이곳에 표시됩니다.
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {actionError && (
              <p
                role="alert"
                className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-600 dark:bg-red-950 dark:text-red-400"
              >
                {actionError}
              </p>
            )}

            {projects.map((project) => (
              <DeletedProjectCard
                key={project.id}
                project={project}
                isDisabled={Boolean(pendingProjectId)}
                pendingAction={
                  pendingProjectId === project.id ? pendingAction : ""
                }
                isConfirmingPermanent={confirmPermanentId === project.id}
                onRestore={() => void handleRestore(project.id)}
                onRequestPermanent={() => {
                  setConfirmPermanentId(project.id);
                  setActionError("");
                }}
                onCancelPermanent={() => setConfirmPermanentId("")}
                onPermanentDelete={() =>
                  void handlePermanentDelete(project.id)
                }
              />
            ))}
          </div>
        )}
      </div>
    </main>
  );
}

function DeletedProjectCard({
  project,
  isDisabled,
  pendingAction,
  isConfirmingPermanent,
  onRestore,
  onRequestPermanent,
  onCancelPermanent,
  onPermanentDelete,
}: {
  project: Project;
  isDisabled: boolean;
  pendingAction: TrashAction;
  isConfirmingPermanent: boolean;
  onRestore: () => void;
  onRequestPermanent: () => void;
  onCancelPermanent: () => void;
  onPermanentDelete: () => void;
}) {
  return (
    <article className="rounded-lg border border-zinc-200 p-4 dark:border-zinc-700">
      <Link
        href={`/projects/${project.id}`}
        className="block rounded-md transition-colors hover:text-zinc-600 dark:hover:text-zinc-300"
      >
        <div className="flex items-start justify-between gap-3">
          <h2 className="break-words font-semibold">{project.name}</h2>
          <span className="shrink-0 rounded-full bg-red-50 px-2 py-0.5 text-xs font-medium text-red-700 dark:bg-red-950 dark:text-red-300">
            삭제됨
          </span>
        </div>
        <p className="mt-1 break-words text-sm text-zinc-500">
          {project.members.join(", ")}
        </p>
        {project.deletedAt && (
          <p className="mt-1 text-xs text-zinc-400">
            삭제일 {project.deletedAt.slice(0, 10)}
          </p>
        )}
      </Link>

      {isConfirmingPermanent && (
        <p className="mt-4 rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700 dark:bg-red-950 dark:text-red-300">
          프로젝트의 회의록과 업무도 함께 삭제되며 되돌릴 수 없습니다.
        </p>
      )}

      <div className="mt-4 flex flex-wrap justify-end gap-2 border-t border-zinc-100 pt-3 dark:border-zinc-800">
        <button
          type="button"
          onClick={onRestore}
          disabled={isDisabled}
          className="rounded-lg border border-zinc-300 px-3 py-1.5 text-sm font-medium transition-colors hover:bg-zinc-100 disabled:cursor-wait disabled:opacity-50 dark:border-zinc-700 dark:hover:bg-zinc-800"
        >
          {pendingAction === "restore" ? "복원 중..." : "복원"}
        </button>
        {isConfirmingPermanent ? (
          <>
            <button
              type="button"
              onClick={onPermanentDelete}
              disabled={isDisabled}
              className="rounded-lg bg-red-600 px-3 py-1.5 text-sm font-medium text-white transition-colors hover:bg-red-700 disabled:cursor-wait disabled:opacity-50"
            >
              {pendingAction === "permanent" ? "삭제 중..." : "영구 삭제 확인"}
            </button>
            <button
              type="button"
              onClick={onCancelPermanent}
              disabled={isDisabled}
              className="rounded-lg px-3 py-1.5 text-sm text-zinc-500 transition-colors hover:bg-zinc-100 disabled:opacity-50 dark:hover:bg-zinc-800"
            >
              취소
            </button>
          </>
        ) : (
          <button
            type="button"
            onClick={onRequestPermanent}
            disabled={isDisabled}
            className="rounded-lg px-3 py-1.5 text-sm text-red-600 transition-colors hover:bg-red-50 disabled:opacity-50 dark:text-red-400 dark:hover:bg-red-950"
          >
            영구 삭제
          </button>
        )}
      </div>
    </article>
  );
}

function TrashIcon({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      className={className}
      aria-hidden="true"
    >
      <path
        strokeLinecap="round"
        d="M4 7h16M9 3.5h6M6.5 7l.7 13h9.6l.7-13"
      />
      <path strokeLinecap="round" d="M10 11v5M14 11v5" />
    </svg>
  );
}
