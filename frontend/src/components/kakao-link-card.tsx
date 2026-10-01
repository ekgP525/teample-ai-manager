"use client";

import { useEffect, useState } from "react";
import {
  getKakaoConnectUrl,
  getKakaoLink,
  sendKakaoTestMessage,
  unlinkKakao,
  updateKakaoPreferences,
} from "@/lib/api/kakao";
import type { KakaoLinkStatus } from "@/types/subscription";

export function KakaoLinkCard({ initialNotice = "" }: { initialNotice?: string }) {
  const [status, setStatus] = useState<KakaoLinkStatus | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState(initialNotice);
  const [isBusy, setIsBusy] = useState(false);
  const [confirmUnlink, setConfirmUnlink] = useState(false);

  useEffect(() => {
    const controller = new AbortController();

    void getKakaoLink(controller.signal)
      .then((data) => {
        setStatus(data);
        setError("");
      })
      .catch((loadError: unknown) => {
        if (controller.signal.aborted) return;
        setError(
          loadError instanceof Error ? loadError.message : "카카오 연결 상태를 불러오지 못했습니다."
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });

    return () => controller.abort();
  }, []);

  const run = async (action: () => Promise<void>, fallback: string) => {
    if (isBusy) return;
    setIsBusy(true);
    setError("");
    setNotice("");
    try {
      await action();
    } catch (actionError) {
      setError(actionError instanceof Error ? actionError.message : fallback);
    } finally {
      setIsBusy(false);
    }
  };

  const connect = () =>
    run(async () => {
      const redirectUri = `${window.location.origin}/kakao/callback`;
      const { url } = await getKakaoConnectUrl(redirectUri);
      window.location.assign(url);
    }, "카카오 연결을 시작하지 못했습니다.");

  const toggleReminders = (enabled: boolean) =>
    run(async () => {
      setStatus(await updateKakaoPreferences(enabled));
      setNotice(enabled ? "마감 알림을 켰습니다." : "마감 알림을 껐습니다.");
    }, "알림 설정을 저장하지 못했습니다.");

  const sendTest = () =>
    run(async () => {
      await sendKakaoTestMessage();
      setNotice("카카오톡 '나와의 채팅'으로 테스트 메시지를 보냈습니다.");
    }, "테스트 메시지를 보내지 못했습니다.");

  const unlink = () =>
    run(async () => {
      await unlinkKakao();
      setStatus((current) => (current ? { ...current, linked: false, deadlineReminders: false } : current));
      setConfirmUnlink(false);
      setNotice("카카오 연결을 해제했습니다.");
    }, "카카오 연결을 해제하지 못했습니다.");

  return (
    <section className="mt-6 rounded-lg border border-zinc-200 p-5 dark:border-zinc-700">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="font-semibold">카카오톡 마감 알림</h2>
        {status?.linked && (
          <span className="rounded-full bg-yellow-100 px-2 py-0.5 text-xs font-semibold text-yellow-900 dark:bg-yellow-900 dark:text-yellow-100">
            연결됨
          </span>
        )}
      </div>

      {isLoading ? (
        <p className="mt-2 text-sm text-zinc-500">연결 상태를 확인하는 중...</p>
      ) : !status?.configured ? (
        <p className="mt-2 text-sm leading-6 text-zinc-500">
          카카오 알림은 서버에 카카오 앱 키가 등록된 뒤 사용할 수 있습니다.
        </p>
      ) : !status.linked ? (
        <div className="mt-2 space-y-3 text-sm text-zinc-600 dark:text-zinc-300">
          <p className="leading-6">
            카카오 계정을 연결하면 내가 맡은 업무의 마감 전날과 당일 아침 9시에 카카오톡 &quot;나와의 채팅&quot;으로 알려 드립니다.
            메시지는 본인에게만 전송되며 다른 사람에게는 보내지 않습니다.
          </p>
          <button
            type="button"
            onClick={() => void connect()}
            disabled={isBusy}
            className="rounded-lg bg-[#FEE500] px-4 py-2 text-sm font-medium text-[#191919] transition-colors hover:bg-[#f5dc00] disabled:opacity-50"
          >
            {isBusy ? "이동 중..." : "카카오 계정 연결"}
          </button>
        </div>
      ) : (
        <div className="mt-3 space-y-3 text-sm text-zinc-600 dark:text-zinc-300">
          {status.needsReconnect && (
            <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:bg-amber-950 dark:text-amber-200">
              카카오 연결이 만료되었습니다. 다시 연결해 주세요.
            </p>
          )}
          <label className="flex cursor-pointer items-center gap-3">
            <input
              type="checkbox"
              checked={status.deadlineReminders}
              onChange={(event) => void toggleReminders(event.target.checked)}
              disabled={isBusy}
              className="h-4 w-4"
            />
            <span>업무 마감 전날·당일 아침 9시에 알림 받기</span>
          </label>
          {status.lastNotifiedAt && (
            <p className="text-xs text-zinc-500">마지막 발송 {formatDateTime(status.lastNotifiedAt)}</p>
          )}
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => void sendTest()}
              disabled={isBusy}
              className="rounded-lg border border-zinc-300 px-3 py-1.5 text-sm transition-colors hover:bg-zinc-100 disabled:opacity-50 dark:border-zinc-700 dark:hover:bg-zinc-800"
            >
              테스트 메시지 보내기
            </button>
            {status.needsReconnect && (
              <button
                type="button"
                onClick={() => void connect()}
                disabled={isBusy}
                className="rounded-lg bg-[#FEE500] px-3 py-1.5 text-sm font-medium text-[#191919] hover:bg-[#f5dc00] disabled:opacity-50"
              >
                다시 연결
              </button>
            )}
            {!confirmUnlink ? (
              <button
                type="button"
                onClick={() => setConfirmUnlink(true)}
                disabled={isBusy}
                className="rounded-lg border border-red-300 px-3 py-1.5 text-sm text-red-600 transition-colors hover:bg-red-50 disabled:opacity-50 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-950"
              >
                연결 해제
              </button>
            ) : (
              <>
                <button
                  type="button"
                  onClick={() => void unlink()}
                  disabled={isBusy}
                  className="rounded-lg bg-red-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
                >
                  해제 확인
                </button>
                <button
                  type="button"
                  onClick={() => setConfirmUnlink(false)}
                  disabled={isBusy}
                  className="rounded-lg border border-zinc-300 px-3 py-1.5 text-sm hover:bg-zinc-100 disabled:opacity-50 dark:border-zinc-700 dark:hover:bg-zinc-800"
                >
                  취소
                </button>
              </>
            )}
          </div>
        </div>
      )}

      {notice && (
        <p role="status" className="mt-3 rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300">
          {notice}
        </p>
      )}
      {error && (
        <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600 dark:bg-red-950 dark:text-red-400">
          {error}
        </p>
      )}
    </section>
  );
}

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}
