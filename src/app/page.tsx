import Link from "next/link";

export default function LandingPage() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center px-4">
      <div className="flex max-w-lg flex-col items-center text-center gap-6">
        <h1 className="text-4xl font-bold tracking-tight sm:text-5xl">
          팀플 AI 매니저
        </h1>
        <p className="text-lg text-zinc-500 leading-relaxed">
          카톡 대화를 붙여넣으면 AI가 회의록을 자동 생성합니다.
          <br />
          할 일 배분, 결정 사항 정리까지 한 번에.
        </p>
        <div className="flex gap-3">
          <Link
            href="/login"
            className="rounded-lg bg-zinc-900 px-5 py-2.5 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
          >
            시작하기
          </Link>
          <Link
            href="/login"
            className="rounded-lg border border-zinc-300 px-5 py-2.5 text-sm font-medium transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
          >
            로그인
          </Link>
        </div>
      </div>
    </main>
  );
}
