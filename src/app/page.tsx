"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";

export default function Home() {
  const router = useRouter();
  const [subject, setSubject] = useState("");
  const [meetingDate, setMeetingDate] = useState("");
  const [members, setMembers] = useState("");
  const [rawText, setRawText] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);

    // TODO: Claude API 호출 → Supabase 저장 → 결과 페이지 이동
    // 지금은 더미 ID로 이동
    const dummyId = crypto.randomUUID();
    router.push(`/result/${dummyId}`);
  };

  const isFormValid = subject && meetingDate && members && rawText;

  return (
    <main className="flex flex-1 flex-col items-center px-4 py-12">
      <div className="w-full max-w-2xl">
        <h1 className="text-3xl font-bold mb-2">팀플 AI 매니저</h1>
        <p className="text-zinc-500 mb-8">
          카톡 대화를 붙여넣으면 AI가 회의록을 자동 생성합니다.
        </p>

        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <label htmlFor="subject" className="text-sm font-medium">
                과목명
              </label>
              <input
                id="subject"
                type="text"
                placeholder="예: 소프트웨어공학"
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
                className="rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <label htmlFor="meetingDate" className="text-sm font-medium">
                회의 날짜
              </label>
              <input
                id="meetingDate"
                type="date"
                value={meetingDate}
                onChange={(e) => setMeetingDate(e.target.value)}
                className="rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
              />
            </div>
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="members" className="text-sm font-medium">
              팀원 이름 (쉼표로 구분)
            </label>
            <input
              id="members"
              type="text"
              placeholder="예: 이다혜, 김철수, 박영희"
              value={members}
              onChange={(e) => setMembers(e.target.value)}
              className="rounded-lg border border-zinc-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="rawText" className="text-sm font-medium">
              카톡 대화 내용
            </label>
            <textarea
              id="rawText"
              rows={12}
              placeholder="카카오톡 단톡방 대화를 복사해서 여기에 붙여넣으세요..."
              value={rawText}
              onChange={(e) => setRawText(e.target.value)}
              className="rounded-lg border border-zinc-300 px-3 py-2 text-sm leading-relaxed focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:border-zinc-700 dark:bg-zinc-900"
            />
          </div>

          <button
            type="submit"
            disabled={!isFormValid || isLoading}
            className="rounded-lg bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-zinc-800 disabled:cursor-not-allowed disabled:opacity-40 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
          >
            {isLoading ? "회의록 생성 중..." : "회의록 생성하기"}
          </button>
        </form>
      </div>
    </main>
  );
}
