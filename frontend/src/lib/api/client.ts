import { getAdminTestSession } from "@/lib/admin-test-auth";
import { supabase } from "@/lib/supabase";

const apiUrl = process.env.NEXT_PUBLIC_API_URL;

export class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
    this.name = "ApiError";
  }
}

type ApiRequestOptions = RequestInit & {
  errorMessage: string;
};

async function getErrorMessage(response: Response, fallback: string) {
  try {
    const data = (await response.json()) as { message?: string };
    return data.message || `${fallback} (${response.status})`;
  } catch {
    return `${fallback} (${response.status})`;
  }
}

async function applyAuthHeaders(headers: Headers) {
  const adminSession = getAdminTestSession();
  if (adminSession) {
    headers.set("X-Admin-Id", adminSession.adminId);
    headers.set("X-Admin-Password", adminSession.password);
    return;
  }

  const {
    data: { session },
  } = await supabase.auth.getSession();

  if (session?.access_token && !headers.has("Authorization")) {
    headers.set("Authorization", `Bearer ${session.access_token}`);
  }
}

export async function apiRequest<T>(
  path: string,
  { errorMessage, headers, ...options }: ApiRequestOptions
): Promise<T> {
  if (!apiUrl) {
    throw new ApiError("Backend API URL is not configured.", 0);
  }

  const requestHeaders = new Headers(headers);
  if (options.body && !requestHeaders.has("Content-Type")) {
    requestHeaders.set("Content-Type", "application/json");
  }
  await applyAuthHeaders(requestHeaders);

  const response = await fetch(`${apiUrl}${path}`, {
    ...options,
    headers: requestHeaders,
  });

  if (!response.ok) {
    throw new ApiError(
      await getErrorMessage(response, errorMessage),
      response.status
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }

  try {
    return (await response.json()) as T;
  } catch {
    throw new ApiError("Invalid server response format.", response.status);
  }
}
