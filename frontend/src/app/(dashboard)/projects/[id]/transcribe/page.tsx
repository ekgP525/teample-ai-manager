"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { ApiError } from "@/lib/api/client";
import { getMyPlan } from "@/lib/api/plan";
import { getProject, getProjectMembers, type ProjectMember } from "@/lib/api/projects";
import {
  createMinutesFromTranscription,
  deleteTranscription,
  getProjectTranscriptions,
  getTranscription,
  updateTranscriptionSpeakers,
  uploadTranscription,
} from "@/lib/api/transcriptions";
import type { Project } from "@/types/minutes";
import type { Transcription, UserPlan } from "@/types/transcription";

const POLL_INTERVAL_MS = 4000;
const ACCEPTED_TYPES =
  ".mp3,.m4a,.wav,.flac,.aac,.ogg,.mp4,.mov,.mkv,.webm,audio/*,video/*";

type Stage = "loading" | "blocked" | "upload" | "processing" | "mapping";

export default function TranscribePage() {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();

  const [project, setProject] = useState<Project | null>(null);
  const [members, setMembers] = useState<ProjectMember[]>([]);
  const [plan, setPlan] = useState<UserPlan | null>(null);
  const [transcription, setTranscription] = useState<Transcription | null>(null);
  const [loadError, setLoadError] = useState("");
  const [isLoading, setIsLoading] = useState(true);

  // 업로드 단계
  const [file, setFile] = useState<File | null>(null);
  const [expectedSpeakers, setExpectedSpeakers] = useState("");
  const [consent, setConsent] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState("");

  // 화자 매핑 단계
  const [speakerNames, setSpeakerNames] = useState<Record<string, string>>({});
  const [title, setTitle] = useState("");
  const [meetingDate, setMeetingDate] = useState("");
  const [isCreating, setIsCreating] = useState(false);
  const [createError, setCreateError] = useState("");
  const [isDiscarding, setIsDiscarding] = useState(false);

  useEffect(() => {
    const controller = new AbortController();

    void Promise.all([
      getProject(id, controller.signal),
      getProjectMembers(id, controller.signal).catch(() => [] as ProjectMember[]),
      getMyPlan(controller.signal),
      getProjectTranscriptions(id, controller.signal).catch(() => [] as Transcription[]),
    ])
      .then(([projectData, memberData, planData, transcriptions]) => {
        setProject(projectData);
        setMembers(memberData);
        setPlan(planData);
        // 회의록으로 이어지지 않은 최신 전사가 있으면 이어서 진행한다.
        const resumable = transcriptions.find(
          (item) => !item.minutesId && item.status !== "FAILED"
        );
        if (resumable) {
          setTranscription(resumable);
          setSpeakerNames(resumable.speakerNames ?? {});
        }
        setLoadError("");
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        setLoadError(
          error instanceof Error ? error.message : "페이지를 불러오지 못했습니다."
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });

    return () => controller.abort();
  }, [id]);

  // 처리 중이면 주기적으로 상태를 확인한다.
  const transcriptionId = transcription?.id;
  const transcriptionStatus = transcription?.status;
  useEffect(() => {
    if (!transcriptionId) return;
    if (transcriptionStatus !== "QUEUED" && transcriptionStatus !== "PROCESSING") return;

    const controller = new AbortController();
    const timer = window.setInterval(() => {
      void getTranscription(id, transcriptionId, controller.signal)
        .then((next) => {
          setTranscription(next);
          if (next.status === "COMPLETED") {
            setSpeakerNames((current) =>
              Object.keys(current).length > 0 ? current : next.speakerNames ?? {}
            );
          }
        })
        .catch(() => {
          // 일시적인 네트워크 오류는 다음 폴링에서 회복된다.
        });
    }, POLL_INTERVAL_MS);

    return () => {
      controller.abort();
      window.clearInterval(timer);
    };
  }, [id, transcriptionId, transcriptionStatus]);

  const stage: Stage = useMemo(() => {
    if (isLoading) return "loading";
    if (!plan?.features.transcription) return "blocked";
    if (!transcription || transcription.status === "FAILED") return "upload";
    if (transcription.status === "COMPLETED") return "mapping";
    return "processing";
  }, [isLoading, plan, transcription]);

  const handleUpload = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!file || isUploading) return;
    if (!consent) {
      setUploadError("회의 참여자 전원의 녹음 동의를 확인해 주세요.");
      return;
    }

    setIsUploading(true);
    setUploadError("");
    try {
      const created = await uploadTranscription(id, {
        file,
        expectedSpeakers: expectedSpeakers ? Number(expectedSpeakers) : null,
        language: "ko",
        consent: true,
      });
      setTranscription(created);
      setSpeakerNames({});
    } catch (error) {
      setUploadError(getUploadErrorMessage(error));
    } finally {
      setIsUploading(false);
    }
  };

  const handleDiscard = async () => {
    if (!transcription || isDiscarding) return;
    setIsDiscarding(true);
    try {
      await deleteTranscription(id, transcription.id);
      setTranscription(null);
      setSpeakerNames({});
      setFile(null);
      setUploadError("");
      setCreateError("");
    } catch (error) {
      setCreateError(
        error instanceof Error ? error.message : "전사를 삭제하지 못했습니다."
      );
    } finally {
      setIsDiscarding(false);
    }
  };

  const handleCreateMinutes = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!transcription || isCreating) return;
    if (!meetingDate) {
      setCreateError("회의 날짜를 선택해 주세요.");
      return;
    }

    setIsCreating(true);
    setCreateError("");
    try {
      await updateTranscriptionSpeakers(id, transcription.id, speakerNames);
      const minutes = await createMinutesFromTranscription(id, transcription.id, {
        title: title.trim(),
        meetingDate,
      });
      router.push(`/projects/${id}/minutes/${minutes.id}`);
    } catch (error) {
      setCreateError(
        error instanceof Error ? error.message : "회의록 생성에 실패했습니다."
      );
      setIsCreating(false);
    }
  };

  if (stage === "loading") {
    return (
      <main className="flex flex-1 items-center justify-center px-4 py-12">
        <p aria-live="polite" className="text-zinc-500">
          준비 중...
        </p>
      </main>
    );
  }

  if (loadError || !project) {
    return (
      <main className="flex flex-1 items-center justify-center px-4 py-12">
        <div
          role="alert"
          className="w-full max-w-lg rounded-lg border border-red-200 bg-red-50 p-6 text-center dark:border-red-900 dark:bg-red-950"
        >
          <p className="text-sm text-red-600 dark:text-red-400">
            {loadError || "프로젝트를 찾을 수 없습니다."}
          </p>
          <Link
            href={`/projects/${id}`}
            className="mt-4 inline-block rounded-lg border border-red-300 px-3 py-1.5 text-sm text-red-600 transition-colors hover:bg-red-100 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-900"
          >
            프로젝트로 돌아가기
          </Link>
        </div>
      </main>
    );
  }

  if (project.status === "DISPOSED" || project.status === "DELETED") {
    return (
      <main className="flex flex-1 items-center justify-center px-4 py-12">
        <div className="text-center">
          <h1 className="text-2xl font-bold">새 회의록을 만들 수 없습니다.</h1>
          <p className="mt-2 text-sm text-zinc-500">
            종료되었거나 삭제된 프로젝트에는 회의록을 추가할 수 없습니다.
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
    <main className="flex flex-1 flex-col items-center px-4 py-8 sm:py-12">
      <div className="w-full max-w-4xl">
        <div className="mb-8">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-bold">음성·영상으로 회의록 만들기</h1>
            <PremiumBadge />
          </div>
          <p className="mt-1 text-sm text-zinc-500">
            회의 녹음이나 영상을 올리면 발언을 글로 옮기고, 화자를 팀원과 연결한 뒤 AI가 회의록을 만듭니다.
          </p>
          {plan && (
            <p className="mt-2 text-xs text-zinc-500">
              이번 달 사용량 {plan.usage.monthMinutesUsed}분 / {plan.usage.monthMinutesLimit}분
            </p>
          )}
        </div>

        {stage === "blocked" && <UpsellCard projectId={id} />}

        {stage === "upload" && (
          <UploadStep
            file={file}
            onFileChange={setFile}
            expectedSpeakers={expectedSpeakers}
            onExpectedSpeakersChange={setExpectedSpeakers}
            memberCount={members.length}
            consent={consent}
            onConsentChange={setConsent}
            isUploading={isUploading}
            error={uploadError}
            failedTranscription={
              transcription?.status === "FAILED" ? transcription : null
            }
            onSubmit={handleUpload}
            onCancel={() => router.push(`/projects/${id}`)}
          />
        )}

        {stage === "processing" && transcription && (
          <ProcessingStep transcription={transcription} />
        )}

        {stage === "mapping" && transcription && (
          <MappingStep
            transcription={transcription}
            members={members}
            speakerNames={speakerNames}
            onSpeakerNameChange={(label, name) =>
              setSpeakerNames((current) => ({ ...current, [label]: name }))
            }
            title={title}
            onTitleChange={setTitle}
            meetingDate={meetingDate}
            onMeetingDateChange={setMeetingDate}
            isCreating={isCreating}
            isDiscarding={isDiscarding}
            error={createError}
            onSubmit={handleCreateMinutes}
            onDiscard={handleDiscard}
          />
        )}
      </div>
    </main>
  );
}

function PremiumBadge() {
  return (
    <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-800 dark:bg-amber-900 dark:text-amber-200">
      프리미엄
    </span>
  );
}

function UpsellCard({ projectId }: { projectId: string }) {
  return (
    <section className="rounded-lg border border-amber-200 bg-amber-50 p-6 dark:border-amber-900 dark:bg-amber-950">
      <h2 className="font-semibold text-amber-900 dark:text-amber-100">
        프리미엄 요금제에서 사용할 수 있는 기능입니다.
      </h2>
      <p className="mt-2 text-sm leading-6 text-amber-800 dark:text-amber-200">
        음성·영상 회의록은 녹음 파일을 글로 옮기고 화자를 구분해 회의록을 만들어 줍니다.
        프로필에서 월 4,900원 구독을 시작하면 바로 사용할 수 있습니다.
      </p>
      <div className="mt-4 flex flex-wrap gap-2">
        <Link
          href="/profile"
          className="rounded-lg bg-amber-800 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-amber-900 dark:bg-amber-200 dark:text-amber-950 dark:hover:bg-amber-100"
        >
          프로필에서 구독 시작
        </Link>
        <Link
          href={`/projects/${projectId}/new`}
          className="rounded-lg border border-amber-300 px-4 py-2 text-sm font-medium text-amber-900 transition-colors hover:bg-amber-100 dark:border-amber-800 dark:text-amber-100 dark:hover:bg-amber-900"
        >
          카톡 대화로 회의록 만들기
        </Link>
      </div>
    </section>
  );
}

function UploadStep({
  file,
  onFileChange,
  expectedSpeakers,
  onExpectedSpeakersChange,
  memberCount,
  consent,
  onConsentChange,
  isUploading,
  error,
  failedTranscription,
  onSubmit,
  onCancel,
}: {
  file: File | null;
  onFileChange: (file: File | null) => void;
  expectedSpeakers: string;
  onExpectedSpeakersChange: (value: string) => void;
  memberCount: number;
  consent: boolean;
  onConsentChange: (value: boolean) => void;
  isUploading: boolean;
  error: string;
  failedTranscription: Transcription | null;
  onSubmit: (event: React.FormEvent) => void;
  onCancel: () => void;
}) {
  const fileInputRef = useRef<HTMLInputElement>(null);

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-6">
      {failedTranscription && (
        <p
          role="alert"
          className="rounded-lg bg-red-50 px-4 py-3 text-sm leading-6 text-red-700 dark:bg-red-950 dark:text-red-300"
        >
          이전 전사가 실패했습니다: {failedTranscription.errorMessage || "알 수 없는 오류"}
          <br />
          다른 파일로 다시 시도해 주세요.
        </p>
      )}

      <section className="rounded-lg border border-zinc-200 p-5 dark:border-zinc-700">
        <h2 className="font-semibold">1. 파일 올리기 또는 바로 녹음</h2>
        <p className="mt-1 text-sm text-zinc-500">
          mp3, m4a, wav, mp4 형식을 권장합니다. 최대 500MB, 4시간까지 처리할 수 있습니다.
        </p>

        <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center">
          <input
            ref={fileInputRef}
            id="media-file"
            type="file"
            accept={ACCEPTED_TYPES}
            disabled={isUploading}
            onChange={(event) => onFileChange(event.target.files?.[0] ?? null)}
            className="block w-full text-sm file:mr-3 file:rounded-lg file:border-0 file:bg-zinc-900 file:px-3 file:py-2 file:text-sm file:font-medium file:text-white hover:file:bg-zinc-800 dark:file:bg-zinc-100 dark:file:text-zinc-900 dark:hover:file:bg-zinc-200"
          />
          <Recorder
            disabled={isUploading}
            onRecorded={(recorded) => {
              onFileChange(recorded);
              if (fileInputRef.current) fileInputRef.current.value = "";
            }}
          />
        </div>

        {file && (
          <p className="mt-3 text-sm text-zinc-600 dark:text-zinc-300">
            선택한 파일: <span className="font-medium">{file.name}</span> ({formatBytes(file.size)})
          </p>
        )}
      </section>

      <section className="rounded-lg border border-zinc-200 p-5 dark:border-zinc-700">
        <h2 className="font-semibold">2. 참석 인원</h2>
        <p className="mt-1 text-sm text-zinc-500">
          말한 사람 수를 알려주면 화자 구분 정확도가 올라갑니다. 모르면 비워 두세요.
        </p>
        <div className="mt-3 flex items-center gap-3">
          <input
            id="expected-speakers"
            type="number"
            min={1}
            max={20}
            inputMode="numeric"
            value={expectedSpeakers}
            onChange={(event) => onExpectedSpeakersChange(event.target.value)}
            disabled={isUploading}
            placeholder={memberCount > 0 ? String(memberCount) : "예: 5"}
            className="w-28 rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
          />
          <label htmlFor="expected-speakers" className="text-sm text-zinc-500">
            명
            {memberCount > 0 && ` (팀원 ${memberCount}명)`}
          </label>
        </div>
      </section>

      <section className="rounded-lg border border-zinc-200 p-5 dark:border-zinc-700">
        <h2 className="font-semibold">3. 녹음 동의 확인</h2>
        <label className="mt-3 flex cursor-pointer items-start gap-3 text-sm leading-6">
          <input
            type="checkbox"
            checked={consent}
            onChange={(event) => onConsentChange(event.target.checked)}
            disabled={isUploading}
            className="mt-1 h-4 w-4"
          />
          <span>
            이 녹음은 회의 참여자 전원의 동의를 받았으며, 참여하지 않은 사람의 대화가 포함되지 않았음을 확인합니다.
            녹음 파일과 전사 내용은 이 프로젝트 팀원만 볼 수 있으며 프로젝트를 영구 삭제하면 함께 삭제됩니다.
          </span>
        </label>
      </section>

      {error && (
        <p
          role="alert"
          className="rounded-lg bg-red-50 px-4 py-2.5 text-sm text-red-600 dark:bg-red-950 dark:text-red-400"
        >
          {error}
        </p>
      )}

      <div className="flex flex-col-reverse gap-2 sm:flex-row">
        <button
          type="button"
          onClick={onCancel}
          disabled={isUploading}
          className="rounded-lg border border-zinc-300 px-4 py-2.5 text-sm transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
        >
          취소
        </button>
        <button
          type="submit"
          disabled={!file || !consent || isUploading}
          aria-busy={isUploading}
          className="flex-1 rounded-lg bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-zinc-800 disabled:cursor-not-allowed disabled:opacity-40 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
        >
          {isUploading ? "업로드 중..." : "업로드하고 전사 시작"}
        </button>
      </div>
    </form>
  );
}

function Recorder({
  disabled,
  onRecorded,
}: {
  disabled: boolean;
  onRecorded: (file: File) => void;
}) {
  const [isRecording, setIsRecording] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [recordError, setRecordError] = useState("");
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<BlobPart[]>([]);
  const timerRef = useRef<number | null>(null);
  const supported =
    typeof window !== "undefined" &&
    typeof MediaRecorder !== "undefined" &&
    !!navigator.mediaDevices?.getUserMedia;

  useEffect(() => {
    return () => {
      if (timerRef.current) window.clearInterval(timerRef.current);
      const recorder = recorderRef.current;
      if (recorder && recorder.state !== "inactive") {
        recorder.stream.getTracks().forEach((track) => track.stop());
        recorder.stop();
      }
    };
  }, []);

  const start = async () => {
    setRecordError("");
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mimeType = pickRecordingMimeType();
      const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
      chunksRef.current = [];
      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) chunksRef.current.push(event.data);
      };
      recorder.onstop = () => {
        stream.getTracks().forEach((track) => track.stop());
        const type = recorder.mimeType || mimeType || "audio/webm";
        const extension = type.includes("mp4") ? "m4a" : type.includes("ogg") ? "ogg" : "webm";
        const blob = new Blob(chunksRef.current, { type });
        const stamp = new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19);
        onRecorded(new File([blob], `recording-${stamp}.${extension}`, { type }));
      };
      recorder.start(1000);
      recorderRef.current = recorder;
      setElapsed(0);
      setIsRecording(true);
      timerRef.current = window.setInterval(() => setElapsed((value) => value + 1), 1000);
    } catch {
      setRecordError("마이크를 사용할 수 없습니다. 브라우저 권한을 확인해 주세요.");
    }
  };

  const stop = () => {
    const recorder = recorderRef.current;
    if (recorder && recorder.state !== "inactive") recorder.stop();
    if (timerRef.current) window.clearInterval(timerRef.current);
    timerRef.current = null;
    setIsRecording(false);
  };

  if (!supported) return null;

  return (
    <div className="flex shrink-0 flex-col gap-1">
      <button
        type="button"
        onClick={() => void (isRecording ? stop() : start())}
        disabled={disabled}
        aria-pressed={isRecording}
        className={`whitespace-nowrap rounded-lg border px-3 py-2 text-sm font-medium transition-colors disabled:opacity-40 ${
          isRecording
            ? "border-red-300 bg-red-50 text-red-700 hover:bg-red-100 dark:border-red-800 dark:bg-red-950 dark:text-red-300"
            : "border-zinc-300 hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
        }`}
      >
        {isRecording ? `녹음 중지 (${formatSeconds(elapsed)})` : "브라우저에서 녹음"}
      </button>
      {recordError && (
        <p className="text-xs text-red-600 dark:text-red-400">{recordError}</p>
      )}
    </div>
  );
}

function ProcessingStep({ transcription }: { transcription: Transcription }) {
  const label =
    transcription.status === "QUEUED"
      ? "처리를 기다리는 중입니다."
      : "음성을 글로 옮기는 중입니다.";

  return (
    <section
      aria-live="polite"
      className="rounded-lg border border-zinc-200 p-8 text-center dark:border-zinc-700"
    >
      <div className="mx-auto mb-4 h-8 w-8 animate-spin rounded-full border-2 border-zinc-300 border-t-zinc-900 dark:border-zinc-700 dark:border-t-zinc-100" />
      <h2 className="font-semibold">{label}</h2>
      <p className="mt-2 text-sm text-zinc-500">
        {transcription.sourceFileName || "녹음 파일"} · 보통 녹음 길이의 10~20% 정도 걸립니다.
        이 화면을 닫아도 처리는 계속되며, 다시 들어오면 이어서 진행할 수 있습니다.
      </p>
    </section>
  );
}

function MappingStep({
  transcription,
  members,
  speakerNames,
  onSpeakerNameChange,
  title,
  onTitleChange,
  meetingDate,
  onMeetingDateChange,
  isCreating,
  isDiscarding,
  error,
  onSubmit,
  onDiscard,
}: {
  transcription: Transcription;
  members: ProjectMember[];
  speakerNames: Record<string, string>;
  onSpeakerNameChange: (label: string, name: string) => void;
  title: string;
  onTitleChange: (value: string) => void;
  meetingDate: string;
  onMeetingDateChange: (value: string) => void;
  isCreating: boolean;
  isDiscarding: boolean;
  error: string;
  onSubmit: (event: React.FormEvent) => void;
  onDiscard: () => void;
}) {
  const samples = useMemo(() => sampleUtterances(transcription), [transcription]);
  const memberNames = members.map((member) => member.displayName);
  const [showTranscript, setShowTranscript] = useState(false);

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-6">
      <section className="rounded-lg border border-emerald-200 bg-emerald-50 px-5 py-4 text-sm dark:border-emerald-900 dark:bg-emerald-950">
        <p className="font-medium text-emerald-800 dark:text-emerald-200">전사가 끝났습니다.</p>
        <p className="mt-1 text-emerald-700 dark:text-emerald-300">
          {transcription.sourceFileName || "녹음 파일"}
          {transcription.durationMs ? ` · ${formatSeconds(Math.round(transcription.durationMs / 1000))}` : ""}
          {` · 발언 ${transcription.segments.length}개 · 화자 ${transcription.speakerLabels.length}명`}
        </p>
      </section>

      <section className="rounded-lg border border-zinc-200 p-5 dark:border-zinc-700">
        <h2 className="font-semibold">1. 화자에 이름 붙이기</h2>
        <p className="mt-1 text-sm text-zinc-500">
          대표 발언을 보고 누구인지 골라 주세요. 비워 두면 &quot;화자 N&quot;으로 표시됩니다.
        </p>
        <ul className="mt-4 space-y-3">
          {transcription.speakerLabels.map((label) => (
            <li
              key={label}
              className="grid gap-3 rounded-lg bg-zinc-50 p-4 sm:grid-cols-[10rem_1fr] dark:bg-zinc-900"
            >
              <div>
                <label htmlFor={`speaker-${label}`} className="text-sm font-medium">
                  화자 {label}
                </label>
                <input
                  id={`speaker-${label}`}
                  type="text"
                  list="member-name-options"
                  value={speakerNames[label] ?? ""}
                  onChange={(event) => onSpeakerNameChange(label, event.target.value)}
                  placeholder="이름 선택 또는 입력"
                  disabled={isCreating}
                  className="mt-1 w-full rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-950"
                />
              </div>
              <ul className="min-w-0 space-y-1 text-sm text-zinc-600 dark:text-zinc-300">
                {(samples[label] ?? []).map((sample) => (
                  <li key={`${label}-${sample.startMs}`} className="flex gap-2">
                    <span className="shrink-0 font-mono text-xs text-zinc-400">
                      {formatSeconds(Math.floor(sample.startMs / 1000))}
                    </span>
                    <span className="min-w-0 break-words">{sample.text}</span>
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
        <datalist id="member-name-options">
          {memberNames.map((name) => (
            <option key={name} value={name} />
          ))}
        </datalist>

        <button
          type="button"
          onClick={() => setShowTranscript((value) => !value)}
          className="mt-4 text-sm text-blue-600 hover:underline dark:text-blue-400"
        >
          {showTranscript ? "전체 전사 숨기기" : "전체 전사 보기"}
        </button>
        {showTranscript && (
          <ol className="mt-3 max-h-80 space-y-1 overflow-y-auto rounded-lg border border-zinc-200 p-3 text-sm dark:border-zinc-700">
            {transcription.segments.map((segment, index) => (
              <li key={`${segment.startMs}-${index}`} className="flex gap-2">
                <span className="shrink-0 font-mono text-xs text-zinc-400">
                  {formatSeconds(Math.floor(segment.startMs / 1000))}
                </span>
                <span className="shrink-0 font-medium">
                  {speakerNames[segment.speaker] || `화자 ${segment.speaker}`}
                </span>
                <span className="min-w-0 break-words text-zinc-700 dark:text-zinc-300">
                  {segment.text}
                </span>
              </li>
            ))}
          </ol>
        )}
      </section>

      <section className="rounded-lg border border-zinc-200 p-5 dark:border-zinc-700">
        <h2 className="font-semibold">2. 회의 정보</h2>
        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="minutes-title" className="text-sm font-medium">
              회의록 제목
            </label>
            <input
              id="minutes-title"
              type="text"
              maxLength={255}
              value={title}
              onChange={(event) => onTitleChange(event.target.value)}
              placeholder="비워두면 AI가 자동 생성"
              disabled={isCreating}
              className="rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="minutes-date" className="text-sm font-medium">
              회의 날짜
            </label>
            <input
              id="minutes-date"
              type="date"
              value={meetingDate}
              onChange={(event) => onMeetingDateChange(event.target.value)}
              required
              disabled={isCreating}
              className="rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
            />
          </div>
        </div>
      </section>

      {error && (
        <p
          role="alert"
          className="rounded-lg bg-red-50 px-4 py-2.5 text-sm text-red-600 dark:bg-red-950 dark:text-red-400"
        >
          {error}
        </p>
      )}

      <div className="flex flex-col-reverse gap-2 sm:flex-row">
        <button
          type="button"
          onClick={onDiscard}
          disabled={isCreating || isDiscarding}
          className="rounded-lg border border-red-300 px-4 py-2.5 text-sm text-red-600 transition-colors hover:bg-red-50 disabled:opacity-40 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-950"
        >
          {isDiscarding ? "삭제 중..." : "이 전사 버리고 다시 올리기"}
        </button>
        <button
          type="submit"
          disabled={!meetingDate || isCreating || isDiscarding}
          aria-busy={isCreating}
          className="flex-1 rounded-lg bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-zinc-800 disabled:cursor-not-allowed disabled:opacity-40 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
        >
          {isCreating ? "회의록 생성 중..." : "회의록 생성하기"}
        </button>
      </div>
    </form>
  );
}

function sampleUtterances(transcription: Transcription) {
  const bySpeaker: Record<string, Transcription["segments"]> = {};
  const sorted = [...transcription.segments].sort(
    (a, b) => b.text.length - a.text.length
  );
  for (const segment of sorted) {
    const list = bySpeaker[segment.speaker] ?? [];
    if (list.length < 3) {
      list.push(segment);
      bySpeaker[segment.speaker] = list;
    }
  }
  for (const label of Object.keys(bySpeaker)) {
    bySpeaker[label].sort((a, b) => a.startMs - b.startMs);
  }
  return bySpeaker;
}

function pickRecordingMimeType() {
  if (typeof MediaRecorder === "undefined") return "";
  const candidates = [
    "audio/mp4",
    "audio/webm;codecs=opus",
    "audio/webm",
    "audio/ogg;codecs=opus",
  ];
  return candidates.find((type) => MediaRecorder.isTypeSupported(type)) ?? "";
}

function getUploadErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 403) return "프리미엄 요금제에서만 사용할 수 있는 기능입니다.";
    if (error.status === 413) return "파일이 너무 큽니다. 500MB 이하로 줄여 주세요.";
  }
  return error instanceof Error ? error.message : "파일을 업로드하지 못했습니다.";
}

function formatBytes(bytes: number) {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))}KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`;
}

function formatSeconds(totalSeconds: number) {
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const mmss = `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
  return hours > 0 ? `${hours}:${mmss}` : mmss;
}
