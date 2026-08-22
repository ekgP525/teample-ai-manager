import { JoinProjectForm } from "@/components/join-project-form";

export default async function JoinProjectPage({
  searchParams,
}: {
  searchParams: Promise<{ code?: string | string[] }>;
}) {
  const codeParam = (await searchParams).code;
  const initialCode = Array.isArray(codeParam) ? codeParam[0] : codeParam || "";

  return <JoinProjectForm initialCode={initialCode} />;
}
