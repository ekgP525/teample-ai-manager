"use client";

import { useEffect, useRef, useState } from "react";
import { ApiError } from "@/lib/api/client";
import {
  createProjectInvitation,
  getActiveProjectInvitation,
  type ProjectInvitation,
} from "@/lib/api/invitations";

interface ProjectInviteDialogProps {
  projectId: string;
  projectName: string;
  onClose: () => void;
}

type CopiedValue = "code" | "link" | null;

export function ProjectInviteDialog({
  projectId,
  projectName,
  onClose,
}: ProjectInviteDialogProps) {
  const [invitation, setInvitation] = useState<ProjectInvitation | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isCreating, setIsCreating] = useState(false);
  const [error, setError] = useState("");
  const [copiedValue, setCopiedValue] = useState<CopiedValue>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    closeButtonRef.current?.focus();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };

    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  useEffect(() => {
    const controller = new AbortController();

    getActiveProjectInvitation(projectId, { signal: controller.signal })
      .then((activeInvitation) => {
        setInvitation(activeInvitation);
        setIsLoading(false);
      })
      .catch((requestError: unknown) => {
        if (controller.signal.aborted) return;
        // 404는 아직 발급된 활성 코드가 없다는 뜻이므로 생성 화면을 보여줍니다.
        if (!(requestError instanceof ApiError && requestError.status === 404)) {
          setError(getInvitationErrorMessage(requestError, "load"));
        }
        setIsLoading(false);
      });

    return () => controller.abort();
  }, [projectId]);

  const createInvitation = async () => {
    setIsCreating(true);
    setError("");
    setCopiedValue(null);

    try {
      setInvitation(await createProjectInvitation(projectId));
    } catch (requestError) {
      setError(getInvitationErrorMessage(requestError, "create"));
    } finally {
      setIsCreating(false);
    }
  };

  const copy = async (value: string, type: Exclude<CopiedValue, null>) => {
    try {
      await navigator.clipboard.writeText(value);
      setCopiedValue(type);
      window.setTimeout(() => setCopiedValue(null), 1800);
    } catch {
      setError("클립보드에 복사하지 못했습니다. 직접 선택해서 복사해 주세요.");
    }
  };

  const inviteUrl = invitation
    ? invitation.inviteUrl ||
      `${window.location.origin}/join?code=${encodeURIComponent(invitation.code)}`
    : "";

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/45 p-0 backdrop-blur-[2px] sm:items-center sm:p-4"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="invite-dialog-title"
        className="w-full max-w-lg rounded-t-2xl border border-zinc-200 bg-white shadow-2xl sm:rounded-xl dark:border-zinc-800 dark:bg-zinc-950"
      >
        <header className="flex items-start justify-between gap-4 border-b border-zinc-200 px-5 py-4 dark:border-zinc-800">
          <div className="min-w-0">
            <h2 id="invite-dialog-title" className="text-lg font-semibold">
              팀원 초대
            </h2>
            <p className="mt-1 truncate text-sm text-zinc-500">{projectName}</p>
          </div>
          <button
            ref={closeButtonRef}
            type="button"
            onClick={onClose}
            aria-label="초대 창 닫기"
            className="-mr-1 flex h-9 w-9 shrink-0 items-center justify-center rounded-md text-zinc-500 transition-colors hover:bg-zinc-100 hover:text-zinc-900 dark:hover:bg-zinc-900 dark:hover:text-zinc-100"
          >
            <CloseIcon />
          </button>
        </header>

        <div className="p-5">
          {error && (
            <p
              role="alert"
              className="mb-4 rounded-lg bg-red-50 px-4 py-3 text-sm leading-5 text-red-600 dark:bg-red-950 dark:text-red-400"
            >
              {error}
            </p>
          )}

          {isLoading ? (
            <p
              role="status"
              className="py-6 text-center text-sm text-zinc-500"
            >
              초대 코드를 확인하는 중...
            </p>
          ) : invitation ? (
            <div className="space-y-5">
              <div>
                <p className="text-sm font-medium">초대 코드</p>
                <div className="mt-2 flex items-center gap-2 rounded-lg bg-zinc-100 p-2 pl-4 dark:bg-zinc-900">
                  <code className="min-w-0 flex-1 break-all text-lg font-semibold tracking-[0.14em]">
                    {invitation.code}
                  </code>
                  <button
                    type="button"
                    onClick={() => void copy(invitation.code, "code")}
                    className="shrink-0 rounded-md bg-white px-3 py-2 text-sm font-medium shadow-sm transition-colors hover:bg-zinc-50 dark:bg-zinc-800 dark:hover:bg-zinc-700"
                  >
                    {copiedValue === "code" ? "복사됨" : "코드 복사"}
                  </button>
                </div>
              </div>

              <div>
                <p className="text-sm font-medium">초대 링크</p>
                <div className="mt-2 flex items-center gap-2 rounded-lg border border-zinc-200 p-2 pl-3 dark:border-zinc-800">
                  <span className="min-w-0 flex-1 truncate text-sm text-zinc-500">
                    {inviteUrl}
                  </span>
                  <button
                    type="button"
                    onClick={() => void copy(inviteUrl, "link")}
                    className="shrink-0 rounded-md border border-zinc-200 px-3 py-2 text-sm font-medium transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-900"
                  >
                    {copiedValue === "link" ? "복사됨" : "링크 복사"}
                  </button>
                </div>
              </div>

              <p className="text-xs leading-5 text-zinc-500">
                이 코드는 {formatExpiry(invitation.expiresAt)}까지 사용할 수 있습니다.
                코드를 받은 팀원은 로그인 후 프로젝트에 참여할 수 있습니다.
              </p>

              <div>
                <button
                  type="button"
                  onClick={() => void createInvitation()}
                  disabled={isCreating}
                  className="w-full rounded-lg border border-zinc-300 px-4 py-2.5 text-sm font-medium transition-colors hover:bg-zinc-100 disabled:cursor-wait disabled:opacity-50 dark:border-zinc-700 dark:hover:bg-zinc-900"
                >
                  {isCreating ? "새 코드 만드는 중..." : "새 초대 코드 만들기"}
                </button>
                <p className="mt-2 text-xs leading-5 text-zinc-500">
                  새 코드를 만들면 지금 코드는 더 이상 사용할 수 없습니다.
                </p>
              </div>
            </div>
          ) : (
            <div>
              <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-zinc-100 text-zinc-700 dark:bg-zinc-900 dark:text-zinc-200">
                <InviteIcon />
              </div>
              <h3 className="mt-4 font-semibold">코드로 팀원을 초대하세요</h3>
              <p className="mt-1 text-sm leading-6 text-zinc-500">
                초대 코드나 링크를 전달하면 팀원이 로그인한 뒤 이 프로젝트에 참여할 수 있습니다.
              </p>

              <button
                type="button"
                onClick={() => void createInvitation()}
                disabled={isCreating}
                className="mt-5 w-full rounded-lg bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-zinc-800 disabled:cursor-wait disabled:opacity-50 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
              >
                {isCreating ? "초대 코드 생성 중..." : "초대 코드 생성"}
              </button>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}

function getInvitationErrorMessage(error: unknown, action: "load" | "create") {
  if (error instanceof ApiError) {
    if (error.status === 401) return "로그인이 만료되었습니다. 다시 로그인해 주세요.";
    if (error.status === 403) return "프로젝트 소유자만 팀원을 초대할 수 있습니다.";
    if (error.status === 404) return "프로젝트를 찾을 수 없습니다. 삭제되었거나 접근할 수 없는 프로젝트입니다.";
  }

  if (error instanceof Error) return error.message;
  return action === "load"
    ? "초대 코드를 불러오지 못했습니다."
    : "초대 코드를 생성하지 못했습니다.";
}

function formatExpiry(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;

  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "long",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function CloseIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8">
      <path strokeLinecap="round" d="m6 6 12 12M18 6 6 18" />
    </svg>
  );
}

function InviteIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="1.8">
      <circle cx="9" cy="8" r="3" />
      <path strokeLinecap="round" d="M3.5 19c.6-3.5 2.4-5.2 5.5-5.2 1.5 0 2.7.4 3.6 1.2M17 10v7M13.5 13.5h7" />
    </svg>
  );
}
