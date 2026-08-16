"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { SignOutButton } from "@/components/sign-out-button";

const navigation = [
  { href: "/dashboard", label: "대시보드", icon: DashboardIcon },
  { href: "/projects", label: "프로젝트", icon: ProjectsIcon },
  { href: "/trash", label: "휴지통", icon: TrashIcon },
  { href: "/profile", label: "프로필", icon: ProfileIcon },
];

export function AppSidebar() {
  const pathname = usePathname();

  return (
    <>
      <aside className="sticky top-0 hidden h-screen w-60 shrink-0 flex-col border-r border-zinc-200 bg-white px-3 py-5 print:hidden md:flex dark:border-zinc-800 dark:bg-zinc-950">
        <Link
          href="/projects"
          className="mb-8 flex items-center gap-3 px-3 text-lg font-bold tracking-tight"
        >
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-zinc-900 text-sm font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900">
            T
          </span>
          팀플 AI
        </Link>

        <nav aria-label="주요 메뉴" className="space-y-1">
          {navigation.map((item) => (
            <SidebarLink
              key={item.href}
              {...item}
              isActive={isPathActive(pathname, item.href)}
            />
          ))}
        </nav>

        <div className="mt-auto border-t border-zinc-200 px-3 pt-4 text-sm dark:border-zinc-800">
          <SignOutButton className="w-full rounded-lg px-3 py-2 text-left transition-colors hover:bg-zinc-100 dark:hover:bg-zinc-900" />
        </div>
      </aside>

      <nav
        aria-label="모바일 주요 메뉴"
        className="fixed inset-x-0 bottom-0 z-40 grid grid-cols-4 border-t border-zinc-200 bg-white/95 px-2 pb-[max(0.5rem,env(safe-area-inset-bottom))] pt-2 backdrop-blur print:hidden md:hidden dark:border-zinc-800 dark:bg-zinc-950/95"
      >
        {navigation.map((item) => {
          const Icon = item.icon;
          const isActive = isPathActive(pathname, item.href);

          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={isActive ? "page" : undefined}
              className={`flex min-w-0 flex-col items-center gap-1 rounded-lg px-1 py-1.5 text-[11px] transition-colors ${
                isActive
                  ? "text-zinc-950 dark:text-zinc-50"
                  : "text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
              }`}
            >
              <Icon className="h-5 w-5" />
              <span className="truncate">{item.label}</span>
            </Link>
          );
        })}
      </nav>
    </>
  );
}

function SidebarLink({
  href,
  label,
  icon: Icon,
  isActive,
}: {
  href: string;
  label: string;
  icon: IconComponent;
  isActive: boolean;
}) {
  return (
    <Link
      href={href}
      aria-current={isActive ? "page" : undefined}
      className={`flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${
        isActive
          ? "bg-zinc-100 text-zinc-950 dark:bg-zinc-900 dark:text-zinc-50"
          : "text-zinc-500 hover:bg-zinc-50 hover:text-zinc-950 dark:text-zinc-400 dark:hover:bg-zinc-900/60 dark:hover:text-zinc-50"
      }`}
    >
      <Icon className="h-5 w-5 shrink-0" />
      {label}
    </Link>
  );
}

function isPathActive(pathname: string, href: string) {
  if (href === "/projects") {
    return pathname === href || pathname.startsWith("/projects/");
  }

  return pathname === href || pathname.startsWith(`${href}/`);
}

type IconComponent = ({ className }: { className?: string }) => React.ReactNode;

function DashboardIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className={className} aria-hidden="true">
      <rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" />
    </svg>
  );
}

function ProjectsIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className={className} aria-hidden="true">
      <path strokeLinecap="round" strokeLinejoin="round" d="M3.5 7.5h6l1.8 2H20.5v8.75A2.25 2.25 0 0 1 18.25 20.5H5.75A2.25 2.25 0 0 1 3.5 18.25V7.5Z" />
      <path strokeLinecap="round" d="M3.5 10h17" />
    </svg>
  );
}

function TrashIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className={className} aria-hidden="true">
      <path strokeLinecap="round" d="M4 7h16M9 3.5h6M6.5 7l.7 13h9.6l.7-13" />
      <path strokeLinecap="round" d="M10 11v5M14 11v5" />
    </svg>
  );
}

function ProfileIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className={className} aria-hidden="true">
      <circle cx="12" cy="8" r="3.5" />
      <path strokeLinecap="round" d="M4.5 20c.8-4 3.2-6 7.5-6s6.7 2 7.5 6" />
    </svg>
  );
}
