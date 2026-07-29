import type { Minutes } from "@/types/minutes";

const DUMMY_MINUTES: Minutes = {
  id: "dummy-1",
  topic: "중간 발표 PPT 역할 분담 및 일정 조율",
  discussions: [
    "데이터 수집 완료 여부 확인 — 전원 완료",
    "발표 자료 제작 도구 논의 — Google Slides vs Canva",
    "발표자 선정 및 스크립트 작성 일정 논의",
    "다음 회의 일정 조율",
  ],
  decisions: [
    "발표 PPT는 Google Slides로 공동 작업",
    "발표자는 이다혜로 확정",
    "다음 회의는 수요일 오후 3시",
  ],
  pending: [
    "교수님 피드백 반영 여부 — 다음 회의에서 결정",
    "추가 설문조사 필요성 검토",
  ],
  todos: [
    { name: "이다혜", task: "발표 스크립트 초안 작성", deadline: "7/23(수)" },
    { name: "박규남", task: "데이터 시각화 차트 3개 제작", deadline: "7/22(화)" },
    { name: "김다희", task: "PPT 디자인 템플릿 세팅", deadline: "7/22(화)" },
  ],
  nextAgenda: [
    "PPT 1차 초안 리뷰",
    "발표 스크립트 피드백",
    "교수님 피드백 반영 여부 최종 결정",
  ],
};

export default async function MinutesPage({
  params,
}: {
  params: Promise<{ id: string; minutesId: string }>;
}) {
  const { id, minutesId } = await params;

  // TODO: API에서 실제 데이터 조회
  const minutes = DUMMY_MINUTES;

  return (
    <main className="flex flex-1 flex-col items-center px-4 py-12">
      <div className="w-full max-w-2xl">
        <div className="mb-8 flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold">회의록</h1>
            <p className="text-sm text-zinc-500">ID: {minutesId}</p>
          </div>
          <div className="flex gap-2">
            <a
              href={`/projects/${id}/minutes/${minutesId}/edit`}
              className="rounded-lg border border-zinc-300 px-3 py-1.5 text-sm transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
            >
              편집
            </a>
            <a
              href={`/projects/${id}`}
              className="rounded-lg border border-zinc-300 px-3 py-1.5 text-sm transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
            >
              새 회의록
            </a>
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
      </div>
    </main>
  );
}
