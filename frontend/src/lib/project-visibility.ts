const HIDDEN_PROJECT_IDS_KEY = "teample:hidden-project-ids";

export function getHiddenProjectIds(): string[] {
  if (typeof window === "undefined") return [];

  try {
    const stored = JSON.parse(
      window.localStorage.getItem(HIDDEN_PROJECT_IDS_KEY) ?? "[]"
    );

    return Array.isArray(stored)
      ? stored.filter((value): value is string => typeof value === "string")
      : [];
  } catch {
    return [];
  }
}

export function removeProjectFromList(projectId: string): string[] {
  const hiddenProjectIds = getHiddenProjectIds();
  const nextHiddenProjectIds = Array.from(
    new Set([...hiddenProjectIds, projectId])
  );

  window.localStorage.setItem(
    HIDDEN_PROJECT_IDS_KEY,
    JSON.stringify(nextHiddenProjectIds)
  );
  return nextHiddenProjectIds;
}
