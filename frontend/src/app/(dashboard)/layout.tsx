import { AuthSessionGuard } from "@/components/auth-session-guard";
import { SignOutButton } from "@/components/sign-out-button";
import Link from "next/link";

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <AuthSessionGuard>
      <header className="border-b border-zinc-200 print:hidden dark:border-zinc-800">
        <nav className="mx-auto flex max-w-4xl items-center justify-between gap-4 py-3 pl-4 pr-16">
          <Link href="/projects" className="text-lg font-bold">
            팀플 AI
          </Link>
          <div className="flex items-center gap-4 text-sm">
            <Link
              href="/dashboard"
              className="text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
            >
              대시보드
            </Link>
            <Link
              href="/projects"
              className="text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
            >
              프로젝트
            </Link>
            <SignOutButton />
          </div>
        </nav>
      </header>
      {children}
    </AuthSessionGuard>
  );
}
