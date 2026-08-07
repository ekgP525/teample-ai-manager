"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { deleteMinutes } from "@/lib/api/minutes";

export function DeleteMinutesButton({
  projectId,
  minutesId,
}: {
  projectId: string;
  minutesId: string;
}) {
  const router = useRouter();
  const [confirming, setConfirming] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState("");

  const handleDelete = async () => {
    if (isDeleting) return;

    setIsDeleting(true);
    setError("");

    try {
      await deleteMinutes(projectId, minutesId);
      router.push(`/projects/${projectId}`);
    } catch (deleteError) {
      setError(
        deleteError instanceof Error
          ? deleteError.message
          : "회의록을 삭제하지 못했습니다."
      );
    } finally {
      setIsDeleting(false);
    }
  };

  if (!confirming) {
    return (
      <button
        type="button"
        onClick={() => {
          setConfirming(true);
          setError("");
        }}
        className="text-sm text-red-500 hover:text-red-700"
      >
        이 회의록 삭제
      </button>
    );
  }

  return (
    <div>
      <div className="flex flex-col items-start gap-3 sm:flex-row sm:items-center">
        <span className="text-sm text-red-600">정말 삭제하시겠습니까?</span>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => void handleDelete()}
            disabled={isDeleting}
            aria-busy={isDeleting}
            className="rounded-lg bg-red-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {isDeleting ? "삭제 중..." : "삭제"}
          </button>
          <button
            type="button"
            onClick={() => {
              setConfirming(false);
              setError("");
            }}
            disabled={isDeleting}
            className="rounded-lg border border-zinc-300 px-3 py-1.5 text-sm hover:bg-zinc-100 disabled:cursor-not-allowed disabled:opacity-50 dark:border-zinc-700 dark:hover:bg-zinc-800"
          >
            취소
          </button>
        </div>
      </div>
      {error && (
        <p
          role="alert"
          className="mt-3 rounded-lg bg-red-50 px-4 py-2.5 text-sm text-red-600 dark:bg-red-950 dark:text-red-400"
        >
          {error}
        </p>
      )}
    </div>
  );
}
