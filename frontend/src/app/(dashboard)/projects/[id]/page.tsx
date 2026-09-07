"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { ProjectTodoBoard } from "@/components/project-todo-board";
import { ProjectInviteDialog } from "@/components/project-invite-dialog";
import { getCurrentAuthUser } from "@/lib/api/auth";
import { ApiError } from "@/lib/api/client";
import { getProjectMinutes } from "@/lib/api/minutes";
import {
  deleteProject,
  getProject,
  getProjectMembers,
  permanentlyDeleteProject,
  restoreProject,
  type ProjectMember,
} from "@/lib/api/projects";
import type { MinutesSummary, Project } from "@/types/minutes";
import { ProjectStatusBadge } from "@/components/project-status-badge";

async function fetchProjectData(projectId: string, signal?: AbortSignal) {
  const [project, minutesList, accountMembers, currentUser] = await Promise.all([
    getProject(projectId, signal),
    getProjectMinutes(projectId, signal),
    getProjectMembers(projectId, signal),
    getCurrentAuthUser(signal),
  ]);

  return { project, minutesList, accountMembers, currentUser };
}

export default function ProjectDetailPage() {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const [project, setProject] = useState<Project | null>(null);
  const [minutesList, setMinutesList] = useState<MinutesSummary[]>([]);
  const [accountMembers, setAccountMembers] = useState<ProjectMember[]>([]);
  const [currentAuthUserId, setCurrentAuthUserId] = useState("");
  const [loadedProjectId, setLoadedProjectId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [projectNotFound, setProjectNotFound] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState("");
  const [isRestoring, setIsRestoring] = useState(false);
  const [confirmPermanentDelete, setConfirmPermanentDelete] = useState(false);
  const [isPermanentlyDeleting, setIsPermanentlyDeleting] = useState(false);
  const [isInviteDialogOpen, setIsInviteDialogOpen] = useState(false);

  useEffect(() => {
    const controller = new AbortController();

    void fetchProjectData(id, controller.signal)
      .then((data) => {
        setProject(data.project);
        setMinutesList(data.minutesList);
        setAccountMembers(data.accountMembers);
        setCurrentAuthUserId(data.currentUser.authUserId);
        setLoadError("");
        setProjectNotFound(false);
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;

        setProject(null);
        setMinutesList([]);
        setAccountMembers([]);
        setCurrentAuthUserId("");

        if (error instanceof ApiError && error.status === 404) {
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
      setAccountMembers(data.accountMembers);
      setCurrentAuthUserId(data.currentUser.authUserId);
    } catch (error) {
      setProject(null);
      setMinutesList([]);
      setAccountMembers([]);
      setCurrentAuthUserId("");

      if (error instanceof ApiError && error.status === 404) {
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
      await deleteProject(id);
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

  const handleRestore = async () => {
    setIsRestoring(true);
    setDeleteError("");

    try {
      const restored = await restoreProject(id);
      setProject(restored);
      setConfirmPermanentDelete(false);
    } catch (error) {
      setDeleteError(
        error instanceof Error
          ? error.message
          : "프로젝트를 복원하지 못했습니다."
      );
    } finally {
      setIsRestoring(false);
    }
  };

  const handlePermanentDelete = async () => {
    setIsPermanentlyDeleting(true);
    setDeleteError("");

    try {
      await permanentlyDeleteProject(id);
      router.replace("/trash");
    } catch (error) {
      setDeleteError(
        error instanceof Error
          ? error.message
          : "프로젝트를 영구 삭제하지 못했습니다."
      );
    } finally {
      setIsPermanentlyDeleting(false);
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

  const isDeletedProject = project.status === "DELETED";
  const isEndedProject = project.status === "DISPOSED";
  const canCreateMinutes = !isDeletedProject && !isEndedProject;
  const hasAccountMembers = accountMembers.length > 0;
  const canManageProject =
    !hasAccountMembers ||
    accountMembers.some(
      (member) =>
        member.userId === currentAuthUserId && member.role === "OWNER"
    );
  const endDate = project.endDate ?? project.disposalDeadline;
  const endedAt = project.endedAt ?? project.disposedAt;
  const memberSummary = hasAccountMembers
    ? accountMembers
        .map(
          (member) =>
            `${member.displayName}${member.role === "OWNER" ? " (소유자)" : ""}`
        )
        .join(", ")
    : project.members.join(", ");

  return (
    <main className="flex flex-1 flex-col items-center px-4 py-8 sm:py-12">
      <div className="w-full max-w-4xl">
        <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <h1 className="break-words text-2xl font-bold">{project.name}</h1>
            <p className="break-words text-sm text-zinc-500">
              팀원: {memberSummary || "등록된 팀원 없음"}
            </p>
            <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
              <ProjectStatusBadge project={project} />
              {endDate && (
                <span className="text-zinc-500">
                  종료 예정일 {endDate}
                </span>
              )}
            </div>
          </div>
          <div className="flex w-full flex-wrap gap-2 sm:w-auto sm:shrink-0">
            {canCreateMinutes && canManageProject && (
              <button
                type="button"
                onClick={() => setIsInviteDialogOpen(true)}
                className="flex-1 rounded-lg border border-zinc-300 px-4 py-2 text-center text-sm font-medium transition-colors hover:bg-zinc-100 sm:flex-none dark:border-zinc-700 dark:hover:bg-zinc-800"
              >
                팀원 초대
              </button>
            )}
            {!isDeletedProject && (
              <Link
                href={`/projects/${id}/dashboard`}
                className="flex-1 rounded-lg border border-zinc-300 px-4 py-2 text-center text-sm font-medium transition-colors hover:bg-zinc-100 sm:flex-none dark:border-zinc-700 dark:hover:bg-zinc-800"
              >
                진행률
              </Link>
            )}
            {canCreateMinutes && (
              <Link
                href={`/projects/${id}/new`}
                className="flex-1 rounded-lg bg-zinc-900 px-4 py-2 text-center text-sm font-medium text-white transition-colors hover:bg-zinc-800 sm:flex-none dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
              >
                새 회의록
              </Link>
            )}
            {!isDeletedProject && canManageProject && (!confirmDelete ? (
              <button
                type="button"
                onClick={() => {
                  setConfirmDelete(true);
                  setDeleteError("");
                }}
                className="rounded-lg border border-red-300 px-3 py-2 text-sm text-red-500 transition-colors hover:bg-red-50 dark:border-red-800 dark:hover:bg-red-950"
              >
                프로젝트 삭제
              </button>
            ) : (
              <div className="flex flex-1 items-center gap-2 sm:flex-none">
                <button
                  type="button"
                  onClick={() => void handleDelete()}
                  disabled={isDeleting}
                  className="flex-1 rounded-lg bg-red-600 px-3 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-50 sm:flex-none"
                >
                  {isDeleting ? "삭제 중..." : "삭제 확인"}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setConfirmDelete(false);
                    setDeleteError("");
                  }}
                  disabled={isDeleting}
                  className="flex-1 rounded-lg border border-zinc-300 px-3 py-2 text-sm hover:bg-zinc-100 disabled:cursor-not-allowed disabled:opacity-50 sm:flex-none dark:border-zinc-700 dark:hover:bg-zinc-800"
                >
                  취소
                </button>
              </div>
            ))}
            {isDeletedProject && canManageProject && (
              <div className="flex w-full flex-wrap gap-2 sm:w-auto">
                <button
                  type="button"
                  onClick={() => void handleRestore()}
                  disabled={isRestoring || isPermanentlyDeleting}
                  className="flex-1 rounded-lg border border-zinc-300 px-3 py-2 text-sm font-medium transition-colors hover:bg-zinc-100 disabled:cursor-wait disabled:opacity-50 sm:flex-none dark:border-zinc-700 dark:hover:bg-zinc-800"
                >
                  {isRestoring ? "복원 중..." : "프로젝트 복원"}
                </button>
                {!confirmPermanentDelete ? (
                  <button
                    type="button"
                    onClick={() => {
                      setConfirmPermanentDelete(true);
                      setDeleteError("");
                    }}
                    disabled={isRestoring}
                    className="flex-1 rounded-lg px-3 py-2 text-sm text-red-600 transition-colors hover:bg-red-50 disabled:opacity-50 sm:flex-none dark:text-red-400 dark:hover:bg-red-950"
                  >
                    영구 삭제
                  </button>
                ) : (
                  <div className="flex flex-1 gap-2 sm:flex-none">
                    <button
                      type="button"
                      onClick={() => void handlePermanentDelete()}
                      disabled={isPermanentlyDeleting}
                      className="flex-1 rounded-lg bg-red-600 px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-red-700 disabled:cursor-wait disabled:opacity-50 sm:flex-none"
                    >
                      {isPermanentlyDeleting ? "삭제 중..." : "영구 삭제 확인"}
                    </button>
                    <button
                      type="button"
                      onClick={() => setConfirmPermanentDelete(false)}
                      disabled={isPermanentlyDeleting}
                      className="flex-1 rounded-lg border border-zinc-300 px-3 py-2 text-sm transition-colors hover:bg-zinc-100 disabled:opacity-50 sm:flex-none dark:border-zinc-700 dark:hover:bg-zinc-800"
                    >
                      취소
                    </button>
                  </div>
                )}
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

        {confirmPermanentDelete && (
          <p className="mb-6 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
            영구 삭제하면 프로젝트의 회의록과 업무도 함께 삭제되며 되돌릴 수 없습니다.
          </p>
        )}

        {isEndedProject && (
          <p className="mb-6 rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-700 dark:bg-amber-950 dark:text-amber-300">
            종료된 프로젝트입니다
            {endedAt ? ` (${endedAt.slice(0, 10)})` : ""}. 기존 회의록과 업무는 확인할 수 있지만 새 회의록은 생성할 수 없습니다.
          </p>
        )}

        {isDeletedProject && (
          <p className="mb-6 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
            삭제된 프로젝트입니다
            {project.deletedAt ? ` (${project.deletedAt.slice(0, 10)})` : ""}. 복원하기 전까지 기존 자료는 읽기 전용으로 표시됩니다.
          </p>
        )}

        <ProjectTodoBoard
          projectId={id}
          minutes={minutesList}
          readOnly={isDeletedProject}
        />

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
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="break-words font-medium">{item.title || item.topic}</p>
                      <p className="text-xs text-zinc-500">
                        {item.meetingDate}
                      </p>
                    </div>
                    <span className="shrink-0 text-xs text-zinc-400">
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
              {canCreateMinutes
                ? "아직 회의록이 없습니다. 새 회의록을 만들어보세요."
                : "저장된 회의록이 없습니다."}
            </p>
          </div>
        )}
      </div>
      {isInviteDialogOpen && (
        <ProjectInviteDialog
          projectId={id}
          projectName={project.name}
          onClose={() => setIsInviteDialogOpen(false)}
        />
      )}
    </main>
  );
}
