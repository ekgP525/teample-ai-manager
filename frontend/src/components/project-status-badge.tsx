import type { Project } from "@/types/minutes";

const ENDING_SOON_DAYS = 7;

type BadgeTone = "active" | "endingSoon" | "ended" | "deleted";

const toneClasses: Record<BadgeTone, string> = {
  active:
    "bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300",
  endingSoon:
    "bg-amber-50 text-amber-700 dark:bg-amber-950 dark:text-amber-300",
  ended: "bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300",
  deleted: "bg-zinc-200 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300",
};

type ProjectStatusSource = Pick<
  Project,
  "status" | "endDate" | "disposalDeadline"
>;

export function ProjectStatusBadge({
  project,
}: {
  project: ProjectStatusSource;
}) {
  const { label, tone } = resolveBadge(project);

  return (
    <span
      className={`shrink-0 whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium ${toneClasses[tone]}`}
    >
      {label}
    </span>
  );
}

function resolveBadge(project: ProjectStatusSource): {
  label: string;
  tone: BadgeTone;
} {
  if (project.status === "DELETED") {
    return { label: "삭제됨", tone: "deleted" };
  }
  if (project.status === "DISPOSED") {
    return { label: "종료됨", tone: "ended" };
  }

  const daysLeft = daysUntil(project.endDate ?? project.disposalDeadline);
  if (daysLeft === null || daysLeft > ENDING_SOON_DAYS) {
    return { label: "진행 중", tone: "active" };
  }
  if (daysLeft < 0) {
    return { label: "종료됨", tone: "ended" };
  }
  if (daysLeft === 0) {
    return { label: "오늘 종료", tone: "endingSoon" };
  }
  return { label: `종료 임박 D-${daysLeft}`, tone: "endingSoon" };
}

function daysUntil(dateText: string | null | undefined): number | null {
  if (!dateText) return null;

  const [year, month, day] = dateText.split("-").map(Number);
  if (!year || !month || !day) return null;

  const target = new Date(year, month - 1, day);
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());

  return Math.round((target.getTime() - today.getTime()) / 86_400_000);
}
