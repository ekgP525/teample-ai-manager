import type { Minutes } from "@/types/minutes";

const DUMMY_MINUTES: Minutes = {
  id: "dummy-1",
  meetingId: "meeting-1",
  summary:
    "중간 발표 PPT 역할 분담 및 다음 회의 일정을 논의함. 데이터 수집은 완료되었으며, 발표 자료는 수요일까지 1차 완성 목표.",
  decisions: [
    "발표 PPT는 Google Slides로 공동 작업",
    "발표자는 이다혜로 확정",
    "다음 회의는 수요일 오후 3시",
  ],
  todos: [
    { name: "이다혜", task: "발표 스크립트 초안 작성", deadline: "7/19(수)" },
    { name: "김철수", task: "데이터 시각화 차트 3개 제작", deadline: "7/18(화)" },
    { name: "박영희", task: "PPT 디자인 템플릿 세팅", deadline: "7/18(화)" },
  ],
  pending: [
    "교수님 피드백 반영 여부 — 다음 회의에서 결정",
    "추가 설문조사 필요성 검토",
  ],
};

export default async function ResultPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;

  // TODO: Supabase에서 실제 데이터 조회
  const minutes = DUMMY_MINUTES;

  return (
    <main className="flex flex-1 flex-col items-center px-4 py-12">
      <div className="w-full max-w-2xl">
        <div className="mb-8 flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold">회의록</h1>
            <p className="text-sm text-zinc-500">ID: {id}</p>
          </div>
          <a
            href="/"
            className="rounded-lg border border-zinc-300 px-3 py-1.5 text-sm transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
          >
            새 회의록
          </a>
        </div>

        <section className="mb-6">
          <h2 className="mb-2 text-lg font-semibold">요약</h2>
          <p className="rounded-lg bg-zinc-50 p-4 text-sm leading-relaxed dark:bg-zinc-900">
            {minutes.summary}
          </p>
        </section>

        <section className="mb-6">
          <h2 className="mb-2 text-lg font-semibold">결정 사항</h2>
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
          <h2 className="mb-2 text-lg font-semibold">할 일 (To-Do)</h2>
          <div className="overflow-hidden rounded-lg border border-zinc-200 dark:border-zinc-700">
            <table className="w-full text-sm">
              <thead className="bg-zinc-50 dark:bg-zinc-900">
                <tr>
                  <th className="px-4 py-2 text-left font-medium">담당</th>
                  <th className="px-4 py-2 text-left font-medium">업무</th>
                  <th className="px-4 py-2 text-left font-medium">마감</th>
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
          <h2 className="mb-2 text-lg font-semibold">미결 사항</h2>
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
      </div>
    </main>
  );
}
