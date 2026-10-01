import Link from "next/link";

export interface LegalSection {
  heading: string;
  paragraphs?: string[];
  items?: string[];
}

export function LegalDocument({
  title,
  effectiveDate,
  intro,
  sections,
}: {
  title: string;
  effectiveDate: string;
  intro: string;
  sections: LegalSection[];
}) {
  return (
    <main className="flex flex-1 flex-col items-center px-4 py-10 sm:py-14">
      <article className="w-full max-w-3xl">
        <header className="mb-8 border-b border-zinc-200 pb-6 dark:border-zinc-800">
          <Link href="/" className="text-sm text-zinc-500 hover:underline">
            팀플 AI 매니저
          </Link>
          <h1 className="mt-2 text-3xl font-bold tracking-tight">{title}</h1>
          <p className="mt-2 text-sm text-zinc-500">시행일 {effectiveDate}</p>
          <p className="mt-4 text-sm leading-7 text-zinc-700 dark:text-zinc-300">{intro}</p>
        </header>

        <div className="space-y-8">
          {sections.map((section, index) => (
            <section key={section.heading}>
              <h2 className="text-lg font-semibold">
                {index + 1}. {section.heading}
              </h2>
              {section.paragraphs?.map((paragraph) => (
                <p key={paragraph} className="mt-2 text-sm leading-7 text-zinc-700 dark:text-zinc-300">
                  {paragraph}
                </p>
              ))}
              {section.items && (
                <ul className="mt-2 list-disc space-y-1.5 pl-5 text-sm leading-7 text-zinc-700 dark:text-zinc-300">
                  {section.items.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              )}
            </section>
          ))}
        </div>

        <footer className="mt-12 flex flex-wrap gap-4 border-t border-zinc-200 pt-6 text-sm text-zinc-500 dark:border-zinc-800">
          <Link href="/privacy" className="hover:underline">
            개인정보처리방침
          </Link>
          <Link href="/terms" className="hover:underline">
            서비스 이용약관
          </Link>
          <Link href="/login" className="hover:underline">
            로그인
          </Link>
        </footer>
      </article>
    </main>
  );
}
