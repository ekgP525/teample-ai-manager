"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { ExportMenu } from "@/components/export-menu";
import { ApiError } from "@/lib/api/client";
import { getMinutes } from "@/lib/api/minutes";
import type { Minutes } from "@/types/minutes";
import { DeleteMinutesButton } from "./delete-button";

export default function MinutesPage() {
  const { id, minutesId } = useParams<{ id: string; minutesId: string }>();
  const [minutes, setMinutes] = useState<Minutes | null>(null);
  const [loadedKey, setLoadedKey] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [minutesNotFound, setMinutesNotFound] = useState(false);
  const currentKey = `${id}/${minutesId}`;

  const loadMinutes = useCallback(
    async (signal?: AbortSignal) => {
      try {
        const data = await getMinutes(id, minutesId, {
          cache: "no-store",
          signal,
        });
        setMinutes(data);
        setLoadError("");
        setMinutesNotFound(false);
      } catch (error) {
        if (signal?.aborted) return;

        setMinutes(null);
        if (error instanceof ApiError && error.status === 404) {
          setMinutesNotFound(true);
        } else {
          setLoadError(
            error instanceof Error
              ? error.message
              : "회의록을 불러오지 못했습니다."
          );
        }
      } finally {
        if (!signal?.aborted) {
          setLoadedKey(currentKey);
          setIsLoading(false);
        }
      }
    },
    [currentKey, id, minutesId]
  );

  const handleRetry = () => {
    setIsLoading(true);
    setLoadError("");
    setMinutesNotFound(false);
    void loadMinutes();
  };

  useEffect(() => {
    const controller = new AbortController();

    void getMinutes(id, minutesId, {
      cache: "no-store",
      signal: controller.signal,
    })
      .then((data) => {
        setMinutes(data);
        setLoadError("");
        setMinutesNotFound(false);
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;

        setMinutes(null);
        if (error instanceof ApiError && error.status === 404) {
          setMinutesNotFound(true);
        } else {
          setLoadError(
            error instanceof Error
              ? error.message
              : "회의록을 불러오지 못했습니다."
          );
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoadedKey(currentKey);
          setIsLoading(false);
        }
      });

    return () => controller.abort();
  }, [currentKey, id, minutesId]);

  if (isLoading || loadedKey !== currentKey) {
    return (
      <main className="flex flex-1 items-center justify-center px-4 py-12">
        <p aria-live="polite" className="text-zinc-500">
          회의록을 불러오는 중...
        </p>
      </main>
    );
  }

  if (loadError) {
    return (
      <main className="flex flex-1 items-center justify-center px-4 py-12">
        <div
          role="alert"
          className="w-full max-w-lg rounded-lg border border-red-200 bg-red-50 p-6 text-center sm:p-8 dark:border-red-900 dark:bg-red-950"
        >
          <p className="text-sm text-red-600 dark:text-red-400">
            {loadError}
          </p>
          <button
            type="button"
            onClick={handleRetry}
            className="mt-4 rounded-lg border border-red-300 px-3 py-1.5 text-sm text-red-600 transition-colors hover:bg-red-100 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-900"
          >
            다시 시도
          </button>
        </div>
      </main>
    );
  }

  if (minutesNotFound || !minutes) {
    return (
      <main className="flex flex-1 items-center justify-center px-4 py-12">
        <div className="text-center">
          <h1 className="text-2xl font-bold">회의록을 찾을 수 없습니다.</h1>
          <p className="mt-2 text-sm text-zinc-500">
            삭제되었거나 잘못된 주소일 수 있습니다.
          </p>
          <Link
            href={`/projects/${id}`}
            className="mt-6 inline-block rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
          >
            프로젝트로 돌아가기
          </Link>
        </div>
      </main>
    );
  }

  return (
    <main className="flex flex-1 flex-col items-center px-4 py-8 sm:py-12 print:p-0">
      <div className="w-full max-w-2xl print:max-w-none">
        <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <h1 className="break-words text-2xl font-bold">
              {minutes.title || "회의록"}
            </h1>
          </div>
          <div className="grid w-full grid-cols-2 gap-2 sm:flex sm:w-auto print:hidden">
            <ExportMenu minutes={minutes} />
            <Link
              href={`/projects/${id}/minutes/${minutesId}/edit`}
              className="rounded-lg border border-zinc-300 px-3 py-1.5 text-center text-sm transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
            >
              편집
            </Link>
            <Link
              href={`/projects/${id}`}
              className="col-span-2 rounded-lg border border-zinc-300 px-3 py-1.5 text-center text-sm transition-colors hover:bg-zinc-100 sm:col-auto dark:border-zinc-700 dark:hover:bg-zinc-800"
            >
              프로젝트로 돌아가기
            </Link>
          </div>
        </div>

        <section className="mb-6">
          <h2 className="mb-2 text-lg font-semibold">회의 주제</h2>
          <p className="break-words rounded-lg bg-zinc-50 p-4 text-sm leading-relaxed dark:bg-zinc-900">
            {minutes.topic || "내용이 없습니다."}
          </p>
        </section>

        <section className="mb-6">
          <h2 className="mb-2 text-lg font-semibold">주요 논의 내용</h2>
          {minutes.discussions.length > 0 ? (
            <ul className="space-y-1.5">
              {minutes.discussions.map((item, i) => (
              <li
                key={i}
                className="flex items-start gap-2 break-words rounded-lg bg-zinc-50 px-4 py-2.5 text-sm dark:bg-zinc-900"
              >
                <span className="mt-0.5 text-blue-600">&#8226;</span>
                {item}
              </li>
              ))}
            </ul>
          ) : (
            <EmptySection />
          )}
        </section>

        <section className="mb-6">
          <h2 className="mb-2 text-lg font-semibold">최종 결정 사항</h2>
          {minutes.decisions.length > 0 ? (
            <ul className="space-y-1.5">
              {minutes.decisions.map((decision, i) => (
              <li
                key={i}
                className="flex items-start gap-2 break-words rounded-lg bg-zinc-50 px-4 py-2.5 text-sm dark:bg-zinc-900"
              >
                <span className="mt-0.5 text-green-600">&#10003;</span>
                {decision}
              </li>
              ))}
            </ul>
          ) : (
            <EmptySection />
          )}
        </section>

        <section className="mb-6">
          <h2 className="mb-2 text-lg font-semibold">미결정 사항</h2>
          {minutes.pending.length > 0 ? (
            <ul className="space-y-1.5">
              {minutes.pending.map((item, i) => (
              <li
                key={i}
                className="flex items-start gap-2 break-words rounded-lg bg-amber-50 px-4 py-2.5 text-sm dark:bg-amber-950"
              >
                <span className="mt-0.5 text-amber-600">&#9679;</span>
                {item}
              </li>
              ))}
            </ul>
          ) : (
            <EmptySection />
          )}
        </section>

        <section className="mb-6">
          <h2 className="mb-2 text-lg font-semibold">담당자별 업무</h2>
          {minutes.todos.length > 0 ? (
            <div className="overflow-x-auto rounded-lg border border-zinc-200 dark:border-zinc-700">
              <table className="w-full min-w-[32rem] text-sm">
              <thead className="bg-zinc-50 dark:bg-zinc-900">
                <tr>
                  <th className="px-4 py-2 text-left font-medium">담당</th>
                  <th className="px-4 py-2 text-left font-medium">업무</th>
                  <th className="px-4 py-2 text-left font-medium">마감일</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-200 dark:divide-zinc-700">
                  {minutes.todos.map((todo, i) => (
                  <tr key={i}>
                    <td className="break-words px-4 py-2 font-medium">{todo.name}</td>
                    <td className="break-words px-4 py-2">{todo.task}</td>
                    <td className="break-words px-4 py-2 text-zinc-500">{todo.deadline}</td>
                  </tr>
                  ))}
              </tbody>
              </table>
            </div>
          ) : (
            <EmptySection />
          )}
        </section>

        <section>
          <h2 className="mb-2 text-lg font-semibold">다음 회의에서 확인할 내용</h2>
          {minutes.nextAgenda.length > 0 ? (
            <ul className="space-y-1.5">
              {minutes.nextAgenda.map((item, i) => (
              <li
                key={i}
                className="flex items-start gap-2 break-words rounded-lg bg-purple-50 px-4 py-2.5 text-sm dark:bg-purple-950"
              >
                <span className="mt-0.5 text-purple-600">&#9654;</span>
                {item}
              </li>
              ))}
            </ul>
          ) : (
            <EmptySection />
          )}
        </section>

        <div className="mt-10 border-t border-zinc-200 pt-6 print:hidden dark:border-zinc-700">
          <DeleteMinutesButton projectId={id} minutesId={minutesId} />
        </div>
      </div>
    </main>
  );
}

function EmptySection() {
  return (
    <p className="rounded-lg bg-zinc-50 px-4 py-2.5 text-sm text-zinc-500 dark:bg-zinc-900">
      내용이 없습니다.
    </p>
  );
}
