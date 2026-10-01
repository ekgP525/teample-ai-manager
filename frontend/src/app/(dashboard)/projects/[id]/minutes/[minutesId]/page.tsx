"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import { ExportMenu } from "@/components/export-menu";
import { ApiError } from "@/lib/api/client";
import { getMinutes } from "@/lib/api/minutes";
import { getProject } from "@/lib/api/projects";
import {
  getTranscription,
  getTranscriptionAudio,
} from "@/lib/api/transcriptions";
import type { Minutes } from "@/types/minutes";
import type { TranscriptSegment, Transcription } from "@/types/transcription";
import { DeleteMinutesButton } from "./delete-button";

export default function MinutesPage() {
  const { id, minutesId } = useParams<{ id: string; minutesId: string }>();
  const [minutes, setMinutes] = useState<Minutes | null>(null);
  const [loadedKey, setLoadedKey] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [minutesNotFound, setMinutesNotFound] = useState(false);
  const [isReadOnly, setIsReadOnly] = useState(false);
  const [transcription, setTranscription] = useState<Transcription | null>(null);
  const [audioUrl, setAudioUrl] = useState("");
  const audioRef = useRef<HTMLAudioElement>(null);
  const currentKey = `${id}/${minutesId}`;
  // 가장 최근 로드 요청 번호. 이전 요청(예: 다른 회의록의 재시도)의 결과는 무시한다.
  const loadRequestRef = useRef(0);

  const loadMinutes = useCallback(
    async (signal?: AbortSignal) => {
      const requestId = ++loadRequestRef.current;
      const isStale = () =>
        signal?.aborted || requestId !== loadRequestRef.current;

      try {
        const [minutesData, projectData] = await Promise.all([
          getMinutes(id, minutesId, {
            cache: "no-store",
            signal,
          }),
          getProject(id, signal),
        ]);
        if (isStale()) return;
        setMinutes(minutesData);
        setIsReadOnly(projectData.status === "DELETED");
        setLoadError("");
        setMinutesNotFound(false);
      } catch (error) {
        if (isStale()) return;

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
        if (!isStale()) {
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
    loadRequestRef.current += 1;

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

  // 음성·영상 전사에서 만든 회의록이면 전사 세그먼트와 녹음 파일을 불러온다.
  const transcriptionId = minutes?.transcriptionId ?? null;
  useEffect(() => {
    if (!transcriptionId) return;

    const controller = new AbortController();
    let objectUrl = "";

    void getTranscription(id, transcriptionId, controller.signal)
      .then((data) => {
        setTranscription(data);
        if (!data.hasAudio) return null;
        return getTranscriptionAudio(id, transcriptionId, controller.signal);
      })
      .then((blob) => {
        if (!blob || controller.signal.aborted) return;
        objectUrl = URL.createObjectURL(blob);
        setAudioUrl(objectUrl);
      })
      .catch(() => {
        // 녹음을 못 불러와도 회의록 본문은 그대로 보여준다.
      });

    return () => {
      controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      setAudioUrl("");
      setTranscription(null);
    };
  }, [id, transcriptionId]);

  const seekTo = useCallback((ms: number) => {
    const audio = audioRef.current;
    if (!audio) return;
    audio.currentTime = ms / 1000;
    void audio.play().catch(() => {
      // 자동 재생이 막히면 사용자가 재생 버튼을 누르면 된다.
    });
    audio.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }, []);

  const jumpFor = (quote: string | undefined) => {
    if (!quote || !transcription || !audioUrl) return null;
    return findEvidenceStart(quote, transcription.segments);
  };

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
              <EvidencePopover quote={minutes.evidence?.title} id="evidence-title" jumpMs={jumpFor(minutes.evidence?.title)} onJump={seekTo} />
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

        {transcription && (
          <section className="mb-6 rounded-lg border border-amber-200 bg-amber-50 p-4 print:hidden dark:border-amber-900 dark:bg-amber-950">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="text-sm font-semibold text-amber-900 dark:text-amber-100">
                회의 녹음
                <span className="ml-2 font-normal text-amber-700 dark:text-amber-300">
                  {transcription.sourceFileName || ""}
                </span>
              </h2>
              <p className="text-xs text-amber-700 dark:text-amber-300">
                항목 옆 재생 버튼을 누르면 해당 발언부터 들을 수 있습니다.
              </p>
            </div>
            {audioUrl ? (
              <audio
                ref={audioRef}
                controls
                preload="metadata"
                src={audioUrl}
                className="mt-3 w-full"
              />
            ) : (
              <p className="mt-3 text-xs text-amber-700 dark:text-amber-300">
                {transcription.hasAudio ? "녹음 파일을 불러오는 중..." : "녹음 파일이 더 이상 서버에 없습니다."}
              </p>
            )}
          </section>
        )}

        <section className="mb-6">
          <h2 className="mb-2 text-lg font-semibold">회의 주제</h2>
          <p
            className="group relative flex items-start gap-2 break-words rounded-lg bg-zinc-50 p-4 text-sm leading-relaxed focus:outline-none focus-visible:ring-2 focus-visible:ring-zinc-400 dark:bg-zinc-900"
            {...evidenceTargetProps(minutes.evidence?.topic, "evidence-topic")}
          >
            <span className="min-w-0 flex-1">{minutes.topic || "내용이 없습니다."}</span>
            <EvidencePopover quote={minutes.evidence?.topic} id="evidence-topic" jumpMs={jumpFor(minutes.evidence?.topic)} onJump={seekTo} />
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
                <EvidencePopover quote={minutes.evidence?.discussions[i]} id={`evidence-discussions-${i}`} jumpMs={jumpFor(minutes.evidence?.discussions[i])} onJump={seekTo} />
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
                <EvidencePopover quote={minutes.evidence?.decisions[i]} id={`evidence-decisions-${i}`} jumpMs={jumpFor(minutes.evidence?.decisions[i])} onJump={seekTo} />
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
                <EvidencePopover quote={minutes.evidence?.pending[i]} id={`evidence-pending-${i}`} jumpMs={jumpFor(minutes.evidence?.pending[i])} onJump={seekTo} />
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
                        <EvidencePopover quote={minutes.evidence?.todos[i]} id={`evidence-todos-${i}`} jumpMs={jumpFor(minutes.evidence?.todos[i])} onJump={seekTo} />
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
                <EvidencePopover quote={minutes.evidence?.nextAgenda[i]} id={`evidence-nextAgenda-${i}`} jumpMs={jumpFor(minutes.evidence?.nextAgenda[i])} onJump={seekTo} />
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

function EvidencePopover({
  quote,
  id,
  jumpMs,
  onJump,
}: {
  quote: string | undefined;
  id: string;
  jumpMs?: number | null;
  onJump?: (ms: number) => void;
}) {
  if (!quote?.trim()) return null;

  return (
    <>
      {jumpMs != null && onJump ? (
        <button
          type="button"
          onClick={(event) => {
            event.stopPropagation();
            onJump(jumpMs);
          }}
          aria-label={`${formatTimestamp(jumpMs)}부터 녹음 재생`}
          className="mt-0.5 shrink-0 select-none whitespace-nowrap rounded px-1 font-mono text-[11px] text-amber-700 transition-colors hover:bg-amber-100 dark:text-amber-300 dark:hover:bg-amber-900 print:hidden"
        >
          &#9654; {formatTimestamp(jumpMs)}
        </button>
      ) : (
        <span
          aria-hidden="true"
          className="mt-0.5 shrink-0 select-none text-xs text-zinc-400 transition-colors group-hover:text-blue-500 group-focus-within:text-blue-500 print:hidden"
        >
          &#10077;
        </span>
      )}
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

/** 근거 인용문이 들어 있는 전사 세그먼트의 시작 시각(ms). 못 찾으면 null. */
function findEvidenceStart(quote: string, segments: TranscriptSegment[]) {
  const normalizedQuote = normalizeForMatch(quote);
  if (!normalizedQuote) return null;

  for (const segment of segments) {
    const text = normalizeForMatch(segment.text);
    if (!text) continue;
    if (text.includes(normalizedQuote) || normalizedQuote.includes(text)) {
      return segment.startMs;
    }
  }

  const head = normalizedQuote.slice(0, 12);
  if (head.length < 6) return null;
  const partial = segments.find((segment) =>
    normalizeForMatch(segment.text).includes(head)
  );
  return partial ? partial.startMs : null;
}

function normalizeForMatch(value: string) {
  return value
    .replace(/^\[[0-9:]+\]\s*[^:]{0,20}:\s*/, "")
    .replace(/[\s"“”'‘’.,!?~…]/g, "")
    .toLowerCase();
}

function formatTimestamp(ms: number) {
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const mmss = `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
  return hours > 0 ? `${hours}:${mmss}` : mmss;
}

function EmptySection() {
  return (
    <p className="rounded-lg bg-zinc-50 px-4 py-2.5 text-sm text-zinc-500 dark:bg-zinc-900">
      내용이 없습니다.
    </p>
  );
}
