import type { Minutes } from "@/types/minutes";
import type { Transcription } from "@/types/transcription";
import { apiRequest } from "./client";

export interface UploadTranscriptionInput {
  file: File;
  expectedSpeakers?: number | null;
  language?: string;
  consent: boolean;
}

export interface CreateMinutesFromTranscriptionInput {
  title: string;
  meetingDate: string;
}

function transcriptionsPath(projectId: string) {
  return `/api/projects/${encodeURIComponent(projectId)}/transcriptions`;
}

function transcriptionPath(projectId: string, transcriptionId: string) {
  return `${transcriptionsPath(projectId)}/${encodeURIComponent(transcriptionId)}`;
}

export function getProjectTranscriptions(projectId: string, signal?: AbortSignal) {
  return apiRequest<Transcription[]>(transcriptionsPath(projectId), {
    errorMessage: "전사 목록을 불러오지 못했습니다.",
    signal,
  });
}

export function getTranscription(
  projectId: string,
  transcriptionId: string,
  signal?: AbortSignal
) {
  return apiRequest<Transcription>(transcriptionPath(projectId, transcriptionId), {
    errorMessage: "전사 정보를 불러오지 못했습니다.",
    cache: "no-store",
    signal,
  });
}

export function uploadTranscription(
  projectId: string,
  input: UploadTranscriptionInput
) {
  const form = new FormData();
  form.append("file", input.file, input.file.name);
  if (input.expectedSpeakers != null && input.expectedSpeakers > 0) {
    form.append("expectedSpeakers", String(input.expectedSpeakers));
  }
  if (input.language) {
    form.append("language", input.language);
  }
  form.append("consent", input.consent ? "true" : "false");

  return apiRequest<Transcription>(transcriptionsPath(projectId), {
    method: "POST",
    body: form,
    errorMessage: "파일을 업로드하지 못했습니다.",
  });
}

export function updateTranscriptionSpeakers(
  projectId: string,
  transcriptionId: string,
  speakerNames: Record<string, string>
) {
  return apiRequest<Transcription>(
    `${transcriptionPath(projectId, transcriptionId)}/speakers`,
    {
      method: "PUT",
      body: JSON.stringify({ speakerNames }),
      errorMessage: "화자 이름을 저장하지 못했습니다.",
    }
  );
}

export function createMinutesFromTranscription(
  projectId: string,
  transcriptionId: string,
  input: CreateMinutesFromTranscriptionInput
) {
  return apiRequest<Minutes>(
    `${transcriptionPath(projectId, transcriptionId)}/minutes`,
    {
      method: "POST",
      body: JSON.stringify(input),
      errorMessage: "회의록 생성에 실패했습니다.",
    }
  );
}

export function getTranscriptionAudio(
  projectId: string,
  transcriptionId: string,
  signal?: AbortSignal
) {
  return apiRequest<Blob>(`${transcriptionPath(projectId, transcriptionId)}/audio`, {
    errorMessage: "녹음 파일을 불러오지 못했습니다.",
    responseType: "blob",
    signal,
  });
}

export function deleteTranscription(projectId: string, transcriptionId: string) {
  return apiRequest<void>(transcriptionPath(projectId, transcriptionId), {
    method: "DELETE",
    errorMessage: "전사를 삭제하지 못했습니다.",
  });
}
