"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { ApiError } from "@/lib/api/client";
import {
  joinProjectInvitation,
  type JoinProjectInvitationResponse,
} from "@/lib/api/invitations";

export function JoinProjectForm({ initialCode }: { initialCode: string }) {
  const router = useRouter();
  const [code, setCode] = useState(normalizeCode(initialCode));
  const [joinedProject, setJoinedProject] =
    useState<JoinProjectInvitationResponse | null>(null);
  const [isJoining, setIsJoining] = useState(false);
  const [error, setError] = useState("");

  const normalizedCode = normalizeCode(code);
  const isValidCode = normalizedCode.length >= 6;

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!isValidCode) {
      setError("초대 코드를 6자 이상 입력해 주세요.");
      return;
    }

    setIsJoining(true);
    setError("");

    try {
      setJoinedProject(await joinProjectInvitation(normalizedCode));
    } catch (requestError) {
      setError(getJoinErrorMessage(requestError));
    } finally {
      setIsJoining(false);
    }
  };

  if (joinedProject) {
    return (
      <main className="flex flex-1 flex-col items-center justify-center px-4 py-12">
        <section className="w-full max-w-md rounded-xl border border-zinc-200 p-7 text-center dark:border-zinc-800">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-emerald-600 dark:bg-emerald-950 dark:text-emerald-300">
            <CheckIcon />
          </div>
          <h1 className="mt-5 text-xl font-bold">프로젝트에 참여했습니다</h1>
          <p className="mt-2 break-words text-sm text-zinc-500">
            {joinedProject.projectName}
          </p>
          <button
            type="button"
            onClick={() =>
              router.replace(
                `/projects/${encodeURIComponent(joinedProject.projectId)}`
              )
            }
            className="mt-6 w-full rounded-lg bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
          >
            프로젝트로 이동
          </button>
        </section>
      </main>
    );
  }

  return (
    <main className="flex flex-1 flex-col items-center px-4 py-8 sm:py-12">
      <div className="w-full max-w-md">
        <div className="mb-8">
          <p className="text-sm font-medium text-zinc-500">프로젝트 참여</p>
          <h1 className="mt-1 text-2xl font-bold">초대 코드를 입력하세요</h1>
          <p className="mt-2 text-sm leading-6 text-zinc-500">
            팀원에게 받은 코드를 입력하면 해당 프로젝트에 참여할 수 있습니다.
          </p>
        </div>

        <form
          onSubmit={handleSubmit}
          className="rounded-xl border border-zinc-200 p-5 dark:border-zinc-800"
        >
          <label htmlFor="invitation-code" className="text-sm font-medium">
            초대 코드
          </label>
          <input
            id="invitation-code"
            value={code}
            onChange={(event) => {
              setCode(normalizeCode(event.target.value));
              setError("");
            }}
            placeholder="예: A7KD-92QM"
            autoComplete="off"
            autoCapitalize="characters"
            spellCheck={false}
            maxLength={32}
            disabled={isJoining}
            aria-describedby="invitation-code-help"
            className="mt-2 w-full rounded-lg border border-zinc-300 px-3 py-3 text-center font-mono text-lg font-semibold tracking-[0.12em] uppercase focus:outline-none focus:ring-2 focus:ring-zinc-900 disabled:opacity-60 dark:border-zinc-700 dark:bg-zinc-900 dark:focus:ring-zinc-100"
          />
          <p id="invitation-code-help" className="mt-2 text-xs text-zinc-500">
            공백은 자동으로 제거되며 영문자는 대문자로 입력됩니다.
          </p>

          {error && (
            <p
              role="alert"
              className="mt-4 rounded-lg bg-red-50 px-4 py-3 text-sm leading-5 text-red-600 dark:bg-red-950 dark:text-red-400"
            >
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={!isValidCode || isJoining}
            className="mt-5 w-full rounded-lg bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-zinc-800 disabled:cursor-not-allowed disabled:opacity-40 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
          >
            {isJoining ? "참여 확인 중..." : "프로젝트 참여"}
          </button>
        </form>

        <p className="mt-5 text-center text-sm text-zinc-500">
          초대 코드가 없나요?{" "}
          <Link
            href="/projects"
            className="font-medium text-zinc-900 hover:underline dark:text-zinc-100"
          >
            프로젝트 목록으로
          </Link>
        </p>
      </div>
    </main>
  );
}

function normalizeCode(value: string) {
  return value.toUpperCase().replace(/\s/g, "");
}

function getJoinErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 404 || error.status === 405) {
      return "초대 API가 아직 연결되지 않았습니다. 백엔드 구현 후 참여할 수 있습니다.";
    }
    if (error.status === 409) return "이미 참여 중이거나 더 이상 사용할 수 없는 코드입니다.";
    if (error.status === 410) return "만료되었거나 취소된 초대 코드입니다.";
    if (error.status === 401) return "로그인 정보를 다시 확인해 주세요.";
  }

  return error instanceof Error
    ? error.message
    : "프로젝트에 참여하지 못했습니다.";
}

function CheckIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="2">
      <path strokeLinecap="round" strokeLinejoin="round" d="m5 12.5 4 4L19 7" />
    </svg>
  );
}
