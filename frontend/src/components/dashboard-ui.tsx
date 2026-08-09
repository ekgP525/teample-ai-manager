"use client";

import Link from "next/link";
import type { DeadlineStatus, TodoAssignment } from "@/types/dashboard";

const deadlineLabels: Record<DeadlineStatus, string> = {
  ON_TRACK: "기한 내",
  OVERDUE: "기한 지남",
  NO_DEADLINE: "기한 없음",
  UNKNOWN_DEADLINE: "기한 미정",
};

const deadlineClasses: Record<DeadlineStatus, string> = {
  ON_TRACK: "bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300",
  OVERDUE: "bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300",
  NO_DEADLINE: "bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300",
  UNKNOWN_DEADLINE: "bg-amber-50 text-amber-700 dark:bg-amber-950 dark:text-amber-300",
};

export function ProgressBar({ value }: { value: number }) {
  const normalized = Math.min(100, Math.max(0, value));

  return (
    <div
      className="h-2 overflow-hidden rounded-full bg-zinc-200 dark:bg-zinc-800"
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={normalized}
    >
      <div
        className="h-full rounded-full bg-blue-600 transition-[width]"
        style={{ width: `${normalized}%` }}
      />
    </div>
  );
}

export function DeadlineBadge({ status }: { status: DeadlineStatus }) {
  return (
    <span
      className={`inline-flex shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${deadlineClasses[status]}`}
    >
      {deadlineLabels[status]}
    </span>
  );
}

export function AssignmentRow({
  assignment,
  isPending,
  onToggle,
}: {
  assignment: TodoAssignment;
  isPending: boolean;
  onToggle: (assignment: TodoAssignment) => void;
}) {
  return (
    <div className="flex items-start gap-3 rounded-lg border border-zinc-200 p-3 dark:border-zinc-700">
      <button
        type="button"
        onClick={() => onToggle(assignment)}
        disabled={isPending}
        aria-label={assignment.completed ? "업무를 진행 중으로 변경" : "업무 완료 처리"}
        className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded border text-xs transition-colors disabled:cursor-wait disabled:opacity-50 ${
          assignment.completed
            ? "border-blue-600 bg-blue-600 text-white"
            : "border-zinc-300 hover:border-blue-500 dark:border-zinc-600"
        }`}
      >
        {assignment.completed ? "✓" : ""}
      </button>
      <div className="min-w-0 flex-1">
        <p
          className={`break-words text-sm ${
            assignment.completed ? "text-zinc-400 line-through" : "font-medium"
          }`}
        >
          {assignment.task}
        </p>
        <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-zinc-500">
          <span>{assignment.memberName}</span>
          <span aria-hidden="true">·</span>
          <span>{assignment.deadline || "기한 미정"}</span>
          {assignment.minutesId && (
            <>
              <span aria-hidden="true">·</span>
              <Link
                href={`/projects/${assignment.projectId}/minutes/${assignment.minutesId}`}
                className="hover:text-zinc-900 hover:underline dark:hover:text-zinc-100"
              >
                {assignment.minutesTitle || "회의록 보기"}
              </Link>
            </>
          )}
        </div>
      </div>
      <DeadlineBadge status={assignment.deadlineStatus} />
    </div>
  );
}
