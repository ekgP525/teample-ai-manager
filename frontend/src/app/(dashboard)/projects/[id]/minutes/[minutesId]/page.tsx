"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { ExportMenu } from "@/components/export-menu";
import { ApiError } from "@/lib/api/client";
import { getMinutes } from "@/lib/api/minutes";
import { getProject } from "@/lib/api/projects";
import type { Minutes } from "@/types/minutes";
import { DeleteMinutesButton } from "./delete-button";

export default function MinutesPage() {
  const { id, minutesId } = useParams<{ id: string; minutesId: string }>();
  const [minutes, setMinutes] = useState<Minutes | null>(null);
  const [loadedKey, setLoadedKey] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [minutesNotFound, setMinutesNotFound] = useState(false);
  const [isReadOnly, setIsReadOnly] = useState(false);
  const currentKey = `${id}/${minutesId}`;

  const loadMinutes = useCallback(
    async (signal?: AbortSignal) => {
      try {
        const [minutesData, projectData] = await Promise.all([
          getMinutes(id, minutesId, {
            cache: "no-store",
            signal,
          }),
          getProject(id, signal),
        ]);
        setMinutes(minutesData);
        setIsReadOnly(projectData.status === "DELETED");
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

    void Promise.all([
      getMinutes(id, minutesId, {
        cache: "no-store",
        signal: controller.signal,
      }),
      getProject(id, controller.signal),
    ])
      .then(([minutesData, projectData]) => {
        setMinutes(minutesData);
        setIsReadOnly(projectData.status === "DELETED");
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
      <div className="w-full max-w-4xl print:max-w-none">
        <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <h1
              className="group relative break-words text-2xl font-bold focus:outline-none focus-visible:ring-2 focus-visible:ring-zinc-400"
              {...evidenceTargetProps(minutes.evidence?.title, "evidence-title")}
            >
              {minutes.title || "회의록"}
              <EvidencePopover quote={minutes.evidence?.title} id="evidence-title" />
            </h1>
          </div>
          <div className="grid w-full grid-cols-2 gap-2 sm:flex sm:w-auto sm:shrink-0 print:hidden">
            <ExportMenu minutes={minutes} />
            {!isReadOnly && (
              <Link
                href={`/projects/${id}/minutes/${minutesId}/edit`}
                className="whitespace-nowrap rounded-lg border border-zinc-300 px-3 py-1.5 text-center text-sm transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
              >
                편집
              </Link>
            )}
            <Link
              href={`/projects/${id}`}
              className="col-span-2 whitespace-nowrap rounded-lg border border-zinc-300 px-3 py-1.5 text-center text-sm transition-colors hover:bg-zinc-100 sm:col-auto dark:border-zinc-700 dark:hover:bg-zinc-800"
            >
              프로젝트로 돌아가기
            </Link>
          </div>
        </div>

        {isReadOnly && (
          <p className="mb-6 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 print:hidden dark:bg-red-950 dark:text-red-300">
            삭제된 프로젝트의 회의록입니다. 프로젝트를 복원하기 전까지 편집하거나 삭제할 수 없습니다.
          </p>
        )}

        <section className="mb-6">
          <h2 className="mb-2 text-lg font-semibold">회의 주제</h2>
          <p
            className="group relative flex items-start gap-2 break-words rounded-lg bg-zinc-50 p-4 text-sm leading-relaxed focus:outline-none focus-visible:ring-2 focus-visible:ring-zinc-400 dark:bg-zinc-900"
            {...evidenceTargetProps(minutes.evidence?.topic, "evidence-topic")}
          >
            <span className="min-w-0 flex-1">{minutes.topic || "내용이 없습니다."}</span>
            <EvidencePopover quote={minutes.evidence?.topic} id="evidence-topic" />
          </p>
        </section>

        <section className="mb-6">
          <h2 className="mb-2 text-lg font-semibold">주요 논의 내용</h2>
          {minutes.discussions.length > 0 ? (
            <ul className="space-y-1.5">
              {minutes.discussions.map((item, i) => (
              <li
                key={i}
                className="group relative flex items-start gap-2 break-words rounded-lg bg-zinc-50 px-4 py-2.5 text-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-zinc-400 dark:bg-zinc-900"
                {...evidenceTargetProps(minutes.evidence?.discussions[i], `evidence-discussions-${i}`)}
              >
                <span className="mt-0.5 text-blue-600">&#8226;</span>
                <span className="min-w-0 flex-1">{item}</span>
                <EvidencePopover quote={minutes.evidence?.discussions[i]} id={`evidence-discussions-${i}`} />
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
                className="group relative flex items-start gap-2 break-words rounded-lg bg-zinc-50 px-4 py-2.5 text-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-zinc-400 dark:bg-zinc-900"
                {...evidenceTargetProps(minutes.evidence?.decisions[i], `evidence-decisions-${i}`)}
              >
                <span className="mt-0.5 text-green-600">&#10003;</span>
                <span className="min-w-0 flex-1">{decision}</span>
                <EvidencePopover quote={minutes.evidence?.decisions[i]} id={`evidence-decisions-${i}`} />
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
                className="group relative flex items-start gap-2 break-words rounded-lg bg-amber-50 px-4 py-2.5 text-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-zinc-400 dark:bg-amber-950"
                {...evidenceTargetProps(minutes.evidence?.pending[i], `evidence-pending-${i}`)}
              >
                <span className="mt-0.5 text-amber-600">&#9679;</span>
                <span className="min-w-0 flex-1">{item}</span>
                <EvidencePopover quote={minutes.evidence?.pending[i]} id={`evidence-pending-${i}`} />
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
                    <td className="whitespace-nowrap px-4 py-2 align-top font-medium">{todo.name}</td>
                    <td className="break-keep px-4 py-2 align-top">
                      <div
                        className="group relative flex items-start gap-2 focus:outline-none focus-visible:ring-2 focus-visible:ring-zinc-400"
                        {...evidenceTargetProps(minutes.evidence?.todos[i], `evidence-todos-${i}`)}
                      >
                        <span className="min-w-0 flex-1">{todo.task}</span>
                        <EvidencePopover quote={minutes.evidence?.todos[i]} id={`evidence-todos-${i}`} />
                      </div>
                    </td>
                    <td className="whitespace-nowrap px-4 py-2 align-top text-zinc-500">{todo.deadline}</td>
                  </tr>
                  ))}
              </tbody>
              </table>
            </div>
          ) : (
            <EmptySection />
          )}
        </section>

        <section className="mb-6">
          <h2 className="mb-2 text-lg font-semibold">다음 회의에서 확인할 내용</h2>
          {minutes.nextAgenda.length > 0 ? (
            <ul className="space-y-1.5">
              {minutes.nextAgenda.map((item, i) => (
              <li
                key={i}
                className="group relative flex items-start gap-2 break-words rounded-lg bg-purple-50 px-4 py-2.5 text-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-zinc-400 dark:bg-purple-950"
                {...evidenceTargetProps(minutes.evidence?.nextAgenda[i], `evidence-nextAgenda-${i}`)}
              >
                <span className="mt-0.5 text-purple-600">&#9654;</span>
                <span className="min-w-0 flex-1">{item}</span>
                <EvidencePopover quote={minutes.evidence?.nextAgenda[i]} id={`evidence-nextAgenda-${i}`} />
              </li>
              ))}
            </ul>
          ) : (
            <EmptySection />
          )}
        </section>

        {!isReadOnly && (
          <div className="mt-10 border-t border-zinc-200 pt-6 print:hidden dark:border-zinc-700">
            <DeleteMinutesButton projectId={id} minutesId={minutesId} />
          </div>
        )}
      </div>
    </main>
  );
}

function evidenceTargetProps(quote: string | undefined, id: string) {
  if (!quote?.trim()) return {};
  return { tabIndex: 0, "aria-describedby": id };
}

function EvidencePopover({ quote, id }: { quote: string | undefined; id: string }) {
  if (!quote?.trim()) return null;

  return (
    <>
      <span
        aria-hidden="true"
        className="mt-0.5 shrink-0 select-none text-xs text-zinc-400 transition-colors group-hover:text-blue-500 group-focus-within:text-blue-500 print:hidden"
      >
        &#10077;
      </span>
      <span
        role="tooltip"
        id={id}
        className="pointer-events-none invisible absolute left-0 top-full z-20 w-full pt-1 opacity-0 transition-opacity duration-100 group-hover:visible group-hover:opacity-100 group-focus-within:visible group-focus-within:opacity-100 print:hidden"
      >
        <span className="block max-w-md rounded-md border border-zinc-200 bg-white px-3 py-2 text-xs leading-5 text-zinc-700 shadow-lg dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-300">
          <span className="mr-1.5 font-medium text-blue-600 dark:text-blue-400">원문</span>
          “{quote}”
        </span>
      </span>
    </>
  );
}

function EmptySection() {
  return (
    <p className="rounded-lg bg-zinc-50 px-4 py-2.5 text-sm text-zinc-500 dark:bg-zinc-900">
      내용이 없습니다.
    </p>
  );
}
