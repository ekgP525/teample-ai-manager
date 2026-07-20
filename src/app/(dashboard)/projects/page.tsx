import Link from "next/link";

export default function ProjectsPage() {
  // TODO: Supabase에서 프로젝트 목록 조회
  return (
    <main className="flex flex-1 flex-col items-center px-4 py-12">
      <div className="w-full max-w-2xl">
        <div className="mb-8 flex items-center justify-between">
          <h1 className="text-2xl font-bold">프로젝트</h1>
          <Link
            href="/projects/new"
            className="rounded-lg bg-zinc-900 px-3 py-1.5 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
          >
            새 프로젝트
          </Link>
        </div>
        <p className="text-zinc-500">아직 생성된 프로젝트가 없습니다.</p>
      </div>
    </main>
  );
}
