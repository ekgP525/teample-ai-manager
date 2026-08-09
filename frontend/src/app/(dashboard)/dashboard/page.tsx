"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { AssignmentRow, ProgressBar } from "@/components/dashboard-ui";
import {
  getMyProjectDashboards,
  updateMyTodoProgress,
} from "@/lib/api/dashboard";
import { getProjects } from "@/lib/api/projects";
import type { MyProjectDashboard, TodoAssignment } from "@/types/dashboard";

export default function DashboardPage() {
  const [members, setMembers] = useState<string[]>([]);
  const [currentUser, setCurrentUser] = useState("");
  const [dashboards, setDashboards] = useState<MyProjectDashboard[]>([]);
  const [isLoadingMembers, setIsLoadingMembers] = useState(true);
  const [membersError, setMembersError] = useState("");
  const [dashboardError, setDashboardError] = useState("");
  const [loadedUser, setLoadedUser] = useState("");
  const [pendingTodoId, setPendingTodoId] = useState("");

  useEffect(() => {
    const controller = new AbortController();

    void getProjects(controller.signal)
      .then((projects) => {
        const nextMembers = Array.from(
          new Set(
            projects.flatMap((project) => project.members || []).filter(Boolean)
          )
        );
        setMembers(nextMembers);
        setCurrentUser((selected) =>
          selected && nextMembers.includes(selected)
            ? selected
            : nextMembers[0] || ""
        );
        setMembersError("");
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setMembersError(
            error instanceof Error
              ? error.message
              : "팀원 목록을 불러오지 못했습니다."
          );
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoadingMembers(false);
      });

    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!currentUser) return;
    const controller = new AbortController();

    void getMyProjectDashboards(currentUser, controller.signal)
      .then((data) => {
        setDashboards(data);
        setDashboardError("");
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setDashboards([]);
          setDashboardError(
            error instanceof Error
              ? error.message
              : "대시보드를 불러오지 못했습니다."
          );
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoadedUser(currentUser);
      });

    return () => controller.abort();
  }, [currentUser]);

  const totals = useMemo(
    () =>
      dashboards.reduce(
        (result, dashboard) => ({
          total: result.total + dashboard.totalTodoCount,
          completed: result.completed + dashboard.completedTodoCount,
          pending: result.pending + dashboard.pendingTodoCount,
        }),
        { total: 0, completed: 0, pending: 0 }
      ),
    [dashboards]
  );

  const reloadDashboards = async () => {
    if (!currentUser) return;
    setLoadedUser("");
    setDashboardError("");

    try {
      setDashboards(await getMyProjectDashboards(currentUser));
    } catch (error) {
      setDashboards([]);
      setDashboardError(
        error instanceof Error
          ? error.message
          : "대시보드를 불러오지 못했습니다."
      );
    } finally {
      setLoadedUser(currentUser);
    }
  };

  const handleToggle = async (assignment: TodoAssignment) => {
    setPendingTodoId(assignment.todoId);
    setDashboardError("");

    try {
      await updateMyTodoProgress(
        assignment.todoId,
        currentUser,
        !assignment.completed
      );
      setDashboards(await getMyProjectDashboards(currentUser));
    } catch (error) {
      setDashboardError(
        error instanceof Error
          ? error.message
          : "업무 상태를 변경하지 못했습니다."
      );
    } finally {
      setPendingTodoId("");
    }
  };

  const isLoadingDashboard = Boolean(currentUser) && loadedUser !== currentUser;

  return (
    <main className="flex flex-1 flex-col items-center px-4 py-8 sm:py-12">
      <div className="w-full max-w-4xl">
        <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold">내 대시보드</h1>
            <p className="mt-1 text-sm text-zinc-500">
              프로젝트별 내 업무와 진행률을 한곳에서 확인합니다.
            </p>
          </div>
          <label className="flex flex-col gap-1 text-sm font-medium">
            현재 사용자
            <select
              value={currentUser}
              onChange={(event) => {
                setCurrentUser(event.target.value);
                setDashboardError("");
              }}
              disabled={isLoadingMembers || members.length === 0}
              className="min-w-40 rounded-lg border border-zinc-300 bg-white px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-900"
            >
              {members.length === 0 && <option value="">팀원 없음</option>}
              {members.map((member) => (
                <option key={member} value={member}>
                  {member}
                </option>
              ))}
            </select>
          </label>
        </div>

        {membersError ? (
          <ErrorState message={membersError} />
        ) : isLoadingMembers || isLoadingDashboard ? (
          <LoadingState />
        ) : !currentUser ? (
          <EmptyState message="프로젝트에 등록된 팀원이 없습니다." />
        ) : dashboardError ? (
          <ErrorState message={dashboardError} onRetry={reloadDashboards} />
        ) : (
          <>
            <section className="mb-8 grid grid-cols-3 gap-3">
              <SummaryCard label="전체 업무" value={totals.total} />
              <SummaryCard label="완료" value={totals.completed} />
              <SummaryCard label="진행 중" value={totals.pending} />
            </section>

            {dashboards.length === 0 ? (
              <EmptyState message={`${currentUser}님에게 배정된 업무가 없습니다.`} />
            ) : (
              <div className="space-y-5">
                {dashboards.map((dashboard) => (
                  <section
                    key={dashboard.projectId}
                    className="rounded-xl border border-zinc-200 p-4 sm:p-5 dark:border-zinc-700"
                  >
                    <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                      <div className="min-w-0">
                        <Link
                          href={`/projects/${dashboard.projectId}`}
                          className="break-words text-lg font-semibold hover:underline"
                        >
                          {dashboard.projectName}
                        </Link>
                        <p className="mt-1 text-sm text-zinc-500">
                          다음 목표: {dashboard.target || "등록된 목표 없음"}
                        </p>
                      </div>
                      <Link
                        href={`/projects/${dashboard.projectId}/dashboard`}
                        className="shrink-0 rounded-lg border border-zinc-300 px-3 py-1.5 text-center text-sm hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
                      >
                        팀 진행률 보기
                      </Link>
                    </div>
                    <div className="mb-4">
                      <div className="mb-1.5 flex justify-between text-sm">
                        <span>
                          {dashboard.completedTodoCount}/{dashboard.totalTodoCount} 완료
                        </span>
                        <strong>{dashboard.progressRate}%</strong>
                      </div>
                      <ProgressBar value={dashboard.progressRate} />
                    </div>
                    <div className="space-y-2">
                      {dashboard.todos.map((assignment) => (
                        <AssignmentRow
                          key={assignment.assignmentId}
                          assignment={assignment}
                          isPending={pendingTodoId === assignment.todoId}
                          onToggle={(item) => void handleToggle(item)}
                        />
                      ))}
                    </div>
                  </section>
                ))}
              </div>
            )}
          </>
        )}
      </div>
    </main>
  );
}

function SummaryCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg bg-zinc-50 p-3 text-center sm:p-4 dark:bg-zinc-900">
      <strong className="block text-xl sm:text-2xl">{value}</strong>
      <span className="text-xs text-zinc-500 sm:text-sm">{label}</span>
    </div>
  );
}

function LoadingState() {
  return (
    <div aria-live="polite" className="rounded-lg border border-zinc-200 p-8 text-center dark:border-zinc-700">
      <p className="text-zinc-500">대시보드를 불러오는 중...</p>
    </div>
  );
}

function EmptyState({ message }: { message: string }) {
  return (
    <div className="rounded-lg border border-zinc-200 p-8 text-center dark:border-zinc-700">
      <p className="text-zinc-500">{message}</p>
    </div>
  );
}

function ErrorState({
  message,
  onRetry,
}: {
  message: string;
  onRetry?: () => void;
}) {
  return (
    <div
      role="alert"
      className="rounded-lg border border-red-200 bg-red-50 p-8 text-center dark:border-red-900 dark:bg-red-950"
    >
      <p className="text-sm text-red-600 dark:text-red-400">{message}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-4 rounded-lg border border-red-300 px-3 py-1.5 text-sm text-red-600 hover:bg-red-100 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-900"
        >
          다시 시도
        </button>
      )}
    </div>
  );
}
