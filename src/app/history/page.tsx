export default function HistoryPage() {
  // TODO: Supabase에서 회의록 목록 조회
  return (
    <main className="flex flex-1 flex-col items-center px-4 py-12">
      <div className="w-full max-w-2xl">
        <div className="mb-8 flex items-center justify-between">
          <h1 className="text-2xl font-bold">회의록 목록</h1>
          <a
            href="/"
            className="rounded-lg border border-zinc-300 px-3 py-1.5 text-sm transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
          >
            새 회의록
          </a>
        </div>
        <p className="text-zinc-500">아직 생성된 회의록이 없습니다.</p>
      </div>
    </main>
  );
}
