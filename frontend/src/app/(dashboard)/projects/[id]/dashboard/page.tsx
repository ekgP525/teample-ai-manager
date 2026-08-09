"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { AssignmentRow, ProgressBar } from "@/components/dashboard-ui";
import {
  getMyProjectDashboard,
  getTeamProjectDashboard,
  updateTodoAssignment,
} from "@/lib/api/dashboard";
import { getProject } from "@/lib/api/projects";
import type {
  MyProjectDashboard,
  TeamProjectDashboard,
  TodoAssignment,
} from "@/types/dashboard";
import type { Project } from "@/types/minutes";

export default function ProjectDashboardPage() {
  const { id } = useParams<{ id: string }>();
  const [project, setProject] = useState<Project | null>(null);
  const [currentUser, setCurrentUser] = useState("");
  const [myDashboard, setMyDashboard] = useState<MyProjectDashboard | null>(null);
  const [teamDashboard, setTeamDashboard] = useState<TeamProjectDashboard | null>(null);
  const [projectError, setProjectError] = useState("");
  const [dashboardError, setDashboardError] = useState("");
  const [loadedProjectId, setLoadedProjectId] = useState("");
  const [loadedDashboardKey, setLoadedDashboardKey] = useState("");
  const [pendingAssignmentId, setPendingAssignmentId] = useState("");
  const dashboardKey = `${id}/${currentUser}`;

  useEffect(() => {
    const controller = new AbortController();

    void getProject(id, controller.signal)
      .then((data) => {
        setProject(data);
        setCurrentUser((selected) =>
          selected && data.members.includes(selected)
            ? selected
            : data.members[0] || ""
        );
        setProjectError("");
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setProject(null);
          setProjectError(
            error instanceof Error
              ? error.message
              : "프로젝트를 불러오지 못했습니다."
          );
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoadedProjectId(id);
      });

    return () => controller.abort();
  }, [id]);

  useEffect(() => {
    if (!currentUser) return;
    const controller = new AbortController();

    void Promise.all([
      getMyProjectDashboard(id, currentUser, controller.signal),
      getTeamProjectDashboard(id, currentUser, controller.signal),
    ])
      .then(([mine, team]) => {
        setMyDashboard(mine);
        setTeamDashboard(team);
        setDashboardError("");
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setMyDashboard(null);
          setTeamDashboard(null);
          setDashboardError(
            error instanceof Error
              ? error.message
              : "프로젝트 대시보드를 불러오지 못했습니다."
          );
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoadedDashboardKey(dashboardKey);
      });

    return () => controller.abort();
  }, [currentUser, dashboardKey, id]);

  const reloadDashboards = async () => {
    if (!currentUser) return;
    setLoadedDashboardKey("");
    setDashboardError("");

    try {
      const [mine, team] = await Promise.all([
        getMyProjectDashboard(id, currentUser),
        getTeamProjectDashboard(id, currentUser),
      ]);
      setMyDashboard(mine);
      setTeamDashboard(team);
    } catch (error) {
      setDashboardError(
        error instanceof Error
          ? error.message
          : "프로젝트 대시보드를 불러오지 못했습니다."
      );
    } finally {
      setLoadedDashboardKey(dashboardKey);
    }
  };

  const handleToggle = async (assignment: TodoAssignment) => {
    setPendingAssignmentId(assignment.assignmentId);
    setDashboardError("");

    try {
      await updateTodoAssignment(
        assignment.assignmentId,
        currentUser,
        !assignment.completed
      );
      const [mine, team] = await Promise.all([
        getMyProjectDashboard(id, currentUser),
        getTeamProjectDashboard(id, currentUser),
      ]);
      setMyDashboard(mine);
      setTeamDashboard(team);
    } catch (error) {
      setDashboardError(
        error instanceof Error
          ? error.message
          : "업무 상태를 변경하지 못했습니다."
      );
    } finally {
      setPendingAssignmentId("");
    }
  };

  if (loadedProjectId !== id) {
    return <PageLoading message="프로젝트를 불러오는 중..." />;
  }

  if (projectError || !project) {
    return <PageError message={projectError || "프로젝트를 찾을 수 없습니다."} />;
  }

  const dashboardIsLoading = Boolean(currentUser) && loadedDashboardKey !== dashboardKey;

  return (
    <main className="flex flex-1 flex-col items-center px-4 py-8 sm:py-12">
      <div className="w-full max-w-4xl">
        <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <Link
              href={`/projects/${id}`}
              className="text-sm text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100"
            >
              ← 프로젝트로 돌아가기
            </Link>
            <h1 className="mt-2 break-words text-2xl font-bold">
              {project.name} 대시보드
            </h1>
          </div>
          <label className="flex flex-col gap-1 text-sm font-medium">
            현재 사용자
            <select
              value={currentUser}
              onChange={(event) => {
                setCurrentUser(event.target.value);
                setDashboardError("");
              }}
              className="min-w-40 rounded-lg border border-zinc-300 bg-white px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-900"
            >
              {project.members.map((member) => (
                <option key={member} value={member}>
                  {member}
                </option>
              ))}
            </select>
          </label>
        </div>

        {!currentUser ? (
          <EmptyState message="프로젝트에 등록된 팀원이 없습니다." />
        ) : dashboardIsLoading ? (
          <PageLoading message="진행률을 불러오는 중..." embedded />
        ) : dashboardError ? (
          <PageError message={dashboardError} onRetry={reloadDashboards} embedded />
        ) : myDashboard && teamDashboard ? (
          <div className="space-y-10">
            <section>
              <div className="mb-4 flex items-end justify-between gap-4">
                <div>
                  <h2 className="text-xl font-semibold">내 진행 상황</h2>
                  <p className="mt-1 text-sm text-zinc-500">
                    다음 목표: {myDashboard.target || "등록된 목표 없음"}
                  </p>
                </div>
                <strong className="text-2xl">{myDashboard.progressRate}%</strong>
              </div>
              <ProgressBar value={myDashboard.progressRate} />
              <p className="mt-2 text-right text-sm text-zinc-500">
                {myDashboard.completedTodoCount}/{myDashboard.totalTodoCount} 완료
              </p>
              <div className="mt-4 space-y-2">
                {myDashboard.todos.length > 0 ? (
                  myDashboard.todos.map((assignment) => (
                    <AssignmentRow
                      key={assignment.assignmentId}
                      assignment={assignment}
                      isPending={pendingAssignmentId === assignment.assignmentId}
                      onToggle={(item) => void handleToggle(item)}
                    />
                  ))
                ) : (
                  <EmptyState message={`${currentUser}님에게 배정된 업무가 없습니다.`} />
                )}
              </div>
            </section>

            <section>
              <h2 className="mb-4 text-xl font-semibold">팀원별 진행률</h2>
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {teamDashboard.members.map((member) => (
                  <div
                    key={member.userId}
                    className="rounded-xl border border-zinc-200 p-4 dark:border-zinc-700"
                  >
                    <div className="mb-3 flex items-center justify-between gap-3">
                      <strong className="break-words">{member.memberName}</strong>
                      <span className="text-sm font-semibold">{member.progressRate}%</span>
                    </div>
                    <ProgressBar value={member.progressRate} />
                    <p className="mt-2 text-xs text-zinc-500">
                      완료 {member.completedTodoCount} · 진행 중 {member.pendingTodoCount}
                    </p>
                  </div>
                ))}
              </div>
            </section>

            <section>
              <h2 className="mb-4 text-xl font-semibold">팀 전체 업무</h2>
              <div className="space-y-3">
                {teamDashboard.todos.map((todo) => (
                  <div
                    key={todo.todoId}
                    className="rounded-xl border border-zinc-200 p-4 dark:border-zinc-700"
                  >
                    <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                      <div className="min-w-0">
                        <p className="break-words font-medium">{todo.task}</p>
                        <p className="mt-1 text-xs text-zinc-500">
                          원래 담당: {todo.sourceAssignee || "미지정"} · 기한 {todo.deadline || "미정"}
                        </p>
                      </div>
                      <Link
                        href={`/projects/${id}/minutes/${todo.minutesId}`}
                        className="shrink-0 text-xs text-zinc-500 hover:underline"
                      >
                        {todo.minutesTitle || "회의록 보기"}
                      </Link>
                    </div>
                    <div className="mt-3 flex flex-wrap gap-2">
                      {todo.assignments.map((assignment) => (
                        <span
                          key={assignment.assignmentId}
                          className={`rounded-full px-2.5 py-1 text-xs ${
                            assignment.completed
                              ? "bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300"
                              : "bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300"
                          }`}
                        >
                          {assignment.memberName} {assignment.completed ? "완료" : "진행 중"}
                        </span>
                      ))}
                    </div>
                  </div>
                ))}
                {teamDashboard.todos.length === 0 && (
                  <EmptyState message="팀 업무가 아직 없습니다." />
                )}
              </div>
            </section>
          </div>
        ) : null}
      </div>
    </main>
  );
}

function EmptyState({ message }: { message: string }) {
  return (
    <div className="rounded-lg border border-zinc-200 p-6 text-center dark:border-zinc-700">
      <p className="text-sm text-zinc-500">{message}</p>
    </div>
  );
}

function PageLoading({
  message,
  embedded = false,
}: {
  message: string;
  embedded?: boolean;
}) {
  const content = (
    <p aria-live="polite" className="text-zinc-500">
      {message}
    </p>
  );

  return embedded ? (
    <div className="rounded-lg border border-zinc-200 p-8 text-center dark:border-zinc-700">
      {content}
    </div>
  ) : (
    <main className="flex flex-1 items-center justify-center px-4 py-12">{content}</main>
  );
}

function PageError({
  message,
  onRetry,
  embedded = false,
}: {
  message: string;
  onRetry?: () => void;
  embedded?: boolean;
}) {
  const content = (
    <div
      role="alert"
      className="w-full max-w-lg rounded-lg border border-red-200 bg-red-50 p-8 text-center dark:border-red-900 dark:bg-red-950"
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

  return embedded ? (
    <div className="flex justify-center">{content}</div>
  ) : (
    <main className="flex flex-1 items-center justify-center px-4 py-12">{content}</main>
  );
}
