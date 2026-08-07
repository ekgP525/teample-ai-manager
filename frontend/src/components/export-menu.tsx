"use client";

import { useEffect, useRef, useState } from "react";
import type { Minutes } from "@/types/minutes";

function normalizeText(value: string) {
  return value.replace(/\r?\n/g, " ").trim();
}

function escapeTableCell(value: string) {
  return normalizeText(value).replace(/\|/g, "\\|");
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function markdownList(items: string[]) {
  return items.length > 0
    ? items.map((item) => `- ${normalizeText(item)}`).join("\n")
    : "- 없음";
}

function htmlList(items: string[]) {
  if (items.length === 0) return "<p>없음</p>";

  return `<ul>${items
    .map((item) => `<li>${escapeHtml(normalizeText(item))}</li>`)
    .join("")}</ul>`;
}

function toMarkdown(minutes: Minutes) {
  const todos =
    minutes.todos.length > 0
      ? [
          "| 담당자 | 업무 | 마감일 |",
          "| --- | --- | --- |",
          ...minutes.todos.map(
            (todo) =>
              `| ${escapeTableCell(todo.name)} | ${escapeTableCell(todo.task)} | ${escapeTableCell(todo.deadline)} |`
          ),
        ].join("\n")
      : "- 없음";

  return [
    `# ${normalizeText(minutes.title) || "회의록"}`,
    "",
    "## 회의 주제",
    "",
    normalizeText(minutes.topic) || "없음",
    "",
    "## 주요 논의 내용",
    "",
    markdownList(minutes.discussions),
    "",
    "## 최종 결정 사항",
    "",
    markdownList(minutes.decisions),
    "",
    "## 미결정 사항",
    "",
    markdownList(minutes.pending),
    "",
    "## 담당자별 업무",
    "",
    todos,
    "",
    "## 다음 회의에서 확인할 내용",
    "",
    markdownList(minutes.nextAgenda),
    "",
  ].join("\n");
}

function toHtml(minutes: Minutes) {
  const todos =
    minutes.todos.length > 0
      ? `<table><thead><tr><th>담당자</th><th>업무</th><th>마감일</th></tr></thead><tbody>${minutes.todos
          .map(
            (todo) =>
              `<tr><td>${escapeHtml(todo.name)}</td><td>${escapeHtml(todo.task)}</td><td>${escapeHtml(todo.deadline)}</td></tr>`
          )
          .join("")}</tbody></table>`
      : "<p>없음</p>";

  return [
    `<h1>${escapeHtml(normalizeText(minutes.title) || "회의록")}</h1>`,
    "<h2>회의 주제</h2>",
    `<p>${escapeHtml(normalizeText(minutes.topic) || "없음")}</p>`,
    "<h2>주요 논의 내용</h2>",
    htmlList(minutes.discussions),
    "<h2>최종 결정 사항</h2>",
    htmlList(minutes.decisions),
    "<h2>미결정 사항</h2>",
    htmlList(minutes.pending),
    "<h2>담당자별 업무</h2>",
    todos,
    "<h2>다음 회의에서 확인할 내용</h2>",
    htmlList(minutes.nextAgenda),
  ].join("");
}

function fileName(title: string) {
  const safeTitle = title
    .replace(/[<>:"/\\|?*\u0000-\u001f]/g, "-")
    .trim()
    .slice(0, 80);

  return safeTitle || "회의록";
}

export function ExportMenu({ minutes }: { minutes: Minutes }) {
  const menuRef = useRef<HTMLDivElement>(null);
  const copiedTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [isOpen, setIsOpen] = useState(false);
  const [isCopied, setIsCopied] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const handlePointerDown = (event: PointerEvent) => {
      if (
        menuRef.current &&
        !menuRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false);
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setIsOpen(false);
    };

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
      if (copiedTimerRef.current) clearTimeout(copiedTimerRef.current);
    };
  }, []);

  const handleCopy = async () => {
    const markdown = toMarkdown(minutes);
    setError("");

    try {
      if (typeof ClipboardItem !== "undefined" && navigator.clipboard.write) {
        await navigator.clipboard.write([
          new ClipboardItem({
            "text/plain": new Blob([markdown], { type: "text/plain" }),
            "text/html": new Blob([toHtml(minutes)], { type: "text/html" }),
          }),
        ]);
      } else {
        await navigator.clipboard.writeText(markdown);
      }

      setIsCopied(true);
      if (copiedTimerRef.current) clearTimeout(copiedTimerRef.current);
      copiedTimerRef.current = setTimeout(() => setIsCopied(false), 2000);
    } catch {
      setError("클립보드에 복사하지 못했습니다.");
    }
  };

  const handleDownload = () => {
    const blob = new Blob([toMarkdown(minutes)], {
      type: "text/markdown;charset=utf-8",
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");

    link.href = url;
    link.download = `${fileName(minutes.title)}.md`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    setIsOpen(false);
  };

  const handlePrint = () => {
    const root = document.documentElement;
    const previousTheme = root.dataset.theme || "light";
    const previousTitle = document.title;

    const restorePage = () => {
      root.dataset.theme = previousTheme;
      document.title = previousTitle;
    };

    setIsOpen(false);
    root.dataset.theme = "light";
    document.title = fileName(minutes.title);
    window.addEventListener("afterprint", restorePage, { once: true });
    window.print();
  };

  return (
    <div ref={menuRef} className="relative print:hidden">
      <button
        type="button"
        onClick={() => {
          setIsOpen((current) => !current);
          setError("");
        }}
        aria-haspopup="menu"
        aria-expanded={isOpen}
        className="rounded-lg border border-zinc-300 px-3 py-1.5 text-sm transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
      >
        내보내기
      </button>

      {isOpen && (
        <div
          role="menu"
          className="absolute right-0 top-full z-20 mt-2 w-52 overflow-hidden rounded-lg border border-zinc-200 bg-white p-1 shadow-lg dark:border-zinc-700 dark:bg-zinc-900"
        >
          <button
            type="button"
            role="menuitem"
            onClick={() => void handleCopy()}
            className="w-full rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-zinc-100 dark:hover:bg-zinc-800"
          >
            {isCopied ? "✓ 복사되었습니다" : "노션에 복사"}
          </button>
          <button
            type="button"
            role="menuitem"
            onClick={handleDownload}
            className="w-full rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-zinc-100 dark:hover:bg-zinc-800"
          >
            Markdown 파일 다운로드
          </button>
          <button
            type="button"
            role="menuitem"
            onClick={handlePrint}
            className="w-full rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-zinc-100 dark:hover:bg-zinc-800"
          >
            PDF로 저장
          </button>
          {error && (
            <p
              role="alert"
              className="border-t border-zinc-200 px-3 py-2 text-xs text-red-600 dark:border-zinc-700 dark:text-red-400"
            >
              {error}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
