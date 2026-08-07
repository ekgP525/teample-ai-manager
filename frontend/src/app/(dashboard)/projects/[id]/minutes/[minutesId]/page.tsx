import Link from "next/link";
import { notFound } from "next/navigation";
import { ExportMenu } from "@/components/export-menu";
import { getMinutes } from "@/lib/api/minutes";
import type { Minutes } from "@/types/minutes";
import { DeleteMinutesButton } from "./delete-button";

async function findMinutes(
  projectId: string,
  minutesId: string
): Promise<Minutes | null> {
  try {
    return await getMinutes(projectId, minutesId, { cache: "no-store" });
  } catch {
    return null;
  }
}

export default async function MinutesPage({
  params,
}: {
  params: Promise<{ id: string; minutesId: string }>;
}) {
  const { id, minutesId } = await params;

  const minutes = await findMinutes(id, minutesId);
  if (!minutes) notFound();

  return (
    <main className="flex flex-1 flex-col items-center px-4 py-12 print:p-0">
      <div className="w-full max-w-2xl print:max-w-none">
        <div className="mb-8 flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold">{minutes.title || "회의록"}</h1>
          </div>
          <div className="flex gap-2 print:hidden">
            <ExportMenu minutes={minutes} />
            <Link
              href={`/projects/${id}/minutes/${minutesId}/edit`}
              className="rounded-lg border border-zinc-300 px-3 py-1.5 text-sm transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
            >
              편집
            </Link>
            <Link
              href={`/projects/${id}`}
              className="rounded-lg border border-zinc-300 px-3 py-1.5 text-sm transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
            >
              프로젝트로 돌아가기
            </Link>
          </div>
        </div>

        <section className="mb-6">
          <h2 className="mb-2 text-lg font-semibold">회의 주제</h2>
          <p className="rounded-lg bg-zinc-50 p-4 text-sm leading-relaxed dark:bg-zinc-900">
            {minutes.topic}
          </p>
        </section>

        <section className="mb-6">
          <h2 className="mb-2 text-lg font-semibold">주요 논의 내용</h2>
          <ul className="space-y-1.5">
            {minutes.discussions.map((item, i) => (
              <li
                key={i}
                className="flex items-start gap-2 rounded-lg bg-zinc-50 px-4 py-2.5 text-sm dark:bg-zinc-900"
              >
                <span className="mt-0.5 text-blue-600">&#8226;</span>
                {item}
              </li>
            ))}
          </ul>
        </section>

        <section className="mb-6">
          <h2 className="mb-2 text-lg font-semibold">최종 결정 사항</h2>
          <ul className="space-y-1.5">
            {minutes.decisions.map((decision, i) => (
              <li
                key={i}
                className="flex items-start gap-2 rounded-lg bg-zinc-50 px-4 py-2.5 text-sm dark:bg-zinc-900"
              >
                <span className="mt-0.5 text-green-600">&#10003;</span>
                {decision}
              </li>
            ))}
          </ul>
        </section>

        <section className="mb-6">
          <h2 className="mb-2 text-lg font-semibold">미결정 사항</h2>
          <ul className="space-y-1.5">
            {minutes.pending.map((item, i) => (
              <li
                key={i}
                className="flex items-start gap-2 rounded-lg bg-amber-50 px-4 py-2.5 text-sm dark:bg-amber-950"
              >
                <span className="mt-0.5 text-amber-600">&#9679;</span>
                {item}
              </li>
            ))}
          </ul>
        </section>

        <section className="mb-6">
          <h2 className="mb-2 text-lg font-semibold">담당자별 업무</h2>
          <div className="overflow-hidden rounded-lg border border-zinc-200 dark:border-zinc-700">
            <table className="w-full text-sm">
              <thead className="bg-zinc-50 dark:bg-zinc-900">
                <tr>
                  <th className="px-4 py-2 text-left font-medium">담당</th>
                  <th className="px-4 py-2 text-left font-medium">업무</th>
                  <th className="px-4 py-2 text-left font-medium">마감일</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-200 dark:divide-zinc-700">
                {minutes.todos.map((todo, i) => (
                  <tr key={i}>
                    <td className="px-4 py-2 font-medium">{todo.name}</td>
                    <td className="px-4 py-2">{todo.task}</td>
                    <td className="px-4 py-2 text-zinc-500">{todo.deadline}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        <section>
          <h2 className="mb-2 text-lg font-semibold">다음 회의에서 확인할 내용</h2>
          <ul className="space-y-1.5">
            {minutes.nextAgenda.map((item, i) => (
              <li
                key={i}
                className="flex items-start gap-2 rounded-lg bg-purple-50 px-4 py-2.5 text-sm dark:bg-purple-950"
              >
                <span className="mt-0.5 text-purple-600">&#9654;</span>
                {item}
              </li>
            ))}
          </ul>
        </section>

        <div className="mt-10 border-t border-zinc-200 pt-6 print:hidden dark:border-zinc-700">
          <DeleteMinutesButton projectId={id} minutesId={minutesId} />
        </div>
      </div>
    </main>
  );
}
