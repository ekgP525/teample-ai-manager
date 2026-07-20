import Link from "next/link";

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  // TODO: 로그인 여부 체크 → 미로그인 시 /login 리다이렉트
  return (
    <>
      <header className="border-b border-zinc-200 dark:border-zinc-800">
        <nav className="mx-auto flex max-w-4xl items-center justify-between px-4 py-3">
          <Link href="/projects" className="text-lg font-bold">
            팀플 AI
          </Link>
          <div className="flex items-center gap-4 text-sm">
            <Link
              href="/projects"
              className="text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
            >
              프로젝트
            </Link>
            <Link
              href="/history"
              className="text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
            >
              회의록
            </Link>
          </div>
        </nav>
      </header>
      {children}
    </>
  );
}
