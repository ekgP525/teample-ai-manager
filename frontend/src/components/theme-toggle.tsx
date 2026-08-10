"use client";

export function ThemeToggle() {
  const toggleTheme = () => {
    const root = document.documentElement;
    const nextTheme = root.dataset.theme === "dark" ? "light" : "dark";

    root.dataset.theme = nextTheme;
    localStorage.setItem("theme", nextTheme);
  };

  return (
    <button
      type="button"
      onClick={toggleTheme}
      aria-label="라이트 모드와 다크 모드 전환"
      title="테마 전환"
      className="absolute right-3 top-2 z-50 flex h-9 w-9 items-center justify-center text-zinc-600 transition-colors hover:text-zinc-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-zinc-400 print:hidden dark:text-zinc-400 dark:hover:text-zinc-50"
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        className="h-6 w-6 dark:hidden"
        aria-hidden="true"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M21 12.8A8.5 8.5 0 1 1 11.2 3a6.5 6.5 0 0 0 9.8 9.8Z"
        />
      </svg>
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        className="hidden h-6 w-6 dark:block"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="4" />
        <path
          strokeLinecap="round"
          d="M12 2v2M12 20v2M4.93 4.93l1.42 1.42M17.65 17.65l1.42 1.42M2 12h2M20 12h2M4.93 19.07l1.42-1.42M17.65 6.35l1.42-1.42"
        />
      </svg>
    </button>
  );
}
