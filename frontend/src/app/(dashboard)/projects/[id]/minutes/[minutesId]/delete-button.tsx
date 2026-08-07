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

  const handleDelete = async () => {
    try {
      await deleteMinutes(projectId, minutesId);
      router.push(`/projects/${projectId}`);
    } catch {
      // 삭제 오류 UI는 다음 화면 안정화 작업에서 처리합니다.
    }
  };

  if (!confirming) {
    return (
      <button
        onClick={() => setConfirming(true)}
        className="text-sm text-red-500 hover:text-red-700"
      >
        이 회의록 삭제
      </button>
    );
  }

  return (
    <div className="flex items-center gap-3">
      <span className="text-sm text-red-600">정말 삭제하시겠습니까?</span>
      <button
        onClick={handleDelete}
        className="rounded-lg bg-red-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-700"
      >
        삭제
      </button>
      <button
        onClick={() => setConfirming(false)}
        className="rounded-lg border border-zinc-300 px-3 py-1.5 text-sm hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
      >
        취소
      </button>
    </div>
  );
}
