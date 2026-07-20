export default function NotFound() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-4">
      <h1 className="text-4xl font-bold">404</h1>
      <p className="text-zinc-500">페이지를 찾을 수 없습니다.</p>
      <a
        href="/"
        className="rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900"
      >
        홈으로
      </a>
    </main>
  );
}
