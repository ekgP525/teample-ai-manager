export type TranscriptionStatus =
  | "QUEUED"
  | "PROCESSING"
  | "COMPLETED"
  | "FAILED";

export interface TranscriptSegment {
  /** STT가 부여한 익명 화자 라벨 ("0", "1", ...) */
  speaker: string;
  startMs: number;
  endMs: number;
  text: string;
}

export interface Transcription {
  id: string;
  projectId: string;
  status: TranscriptionStatus;
  sourceFileName: string | null;
  mediaKind: "AUDIO" | "VIDEO" | null;
  durationMs: number | null;
  expectedSpeakers: number | null;
  speakerLabels: string[];
  speakerNames: Record<string, string>;
  segments: TranscriptSegment[];
  errorMessage: string | null;
  minutesId: string | null;
  hasAudio: boolean;
  createdAt: string;
  completedAt: string | null;
}

export type PlanType = "FREE" | "PREMIUM";

export interface UserPlan {
  plan: PlanType;
  premium: boolean;
  expiresAt: string | null;
  features: {
    transcription: boolean;
  };
  usage: {
    monthMinutesUsed: number;
    monthMinutesLimit: number;
  };
}
