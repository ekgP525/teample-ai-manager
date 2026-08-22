import { supabase } from "@/lib/supabase";

const apiUrl = process.env.NEXT_PUBLIC_API_URL;
let isRedirectingToLogin = false;

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

export async function apiRequest<T>(
  path: string,
  { errorMessage, headers, ...options }: ApiRequestOptions
): Promise<T> {
  if (!apiUrl) {
    throw new ApiError("백엔드 API 주소가 설정되지 않았습니다.", 0);
  }

  const accessToken = await getAccessToken();
  return sendRequest<T>(path, errorMessage, headers, options, accessToken, true);
}

async function sendRequest<T>(
  path: string,
  errorMessage: string,
  headers: HeadersInit | undefined,
  options: Omit<RequestInit, "headers">,
  accessToken: string,
  allowTokenRefresh: boolean
): Promise<T> {
  const requestHeaders = new Headers(headers);
  requestHeaders.set("Authorization", `Bearer ${accessToken}`);

  if (options.body && !requestHeaders.has("Content-Type")) {
    requestHeaders.set("Content-Type", "application/json");
  }

  const response = await fetch(`${apiUrl}${path}`, {
    ...options,
    headers: requestHeaders,
  });

  if (response.status === 401 && allowTokenRefresh && !options.signal?.aborted) {
    const refreshedToken = await refreshAccessToken();
    if (refreshedToken) {
      return sendRequest<T>(
        path,
        errorMessage,
        headers,
        options,
        refreshedToken,
        false
      );
    }
  }

  if (!response.ok) {
    if (response.status === 401) {
      void redirectToLogin(true);
      throw new ApiError("로그인이 만료되었습니다. 다시 로그인해 주세요.", 401);
    }

    if (response.status === 403) {
      throw new ApiError(
        "이 프로젝트에 접근하거나 작업을 수행할 권한이 없습니다.",
        403
      );
    }

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
    throw new ApiError("서버 응답 형식이 올바르지 않습니다.", response.status);
  }
}

async function getAccessToken() {
  const { data, error } = await supabase.auth.getSession();

  if (error || !data.session?.access_token) {
    void redirectToLogin(false);
    throw new ApiError("로그인 후 이용해 주세요.", 401);
  }

  return data.session.access_token;
}

async function refreshAccessToken() {
  try {
    const { data, error } = await supabase.auth.refreshSession();
    if (error) return null;
    return data.session?.access_token || null;
  } catch {
    return null;
  }
}

async function redirectToLogin(clearLocalSession: boolean) {
  if (typeof window === "undefined" || isRedirectingToLogin) return;

  isRedirectingToLogin = true;
  const next = `${window.location.pathname}${window.location.search}`;

  if (clearLocalSession) {
    try {
      await supabase.auth.signOut({ scope: "local" });
    } catch {
      // 로그인 화면으로 이동해 사용자가 세션을 다시 만들 수 있게 합니다.
    }
  }

  const query = new URLSearchParams({
    next,
    reason: clearLocalSession ? "session-expired" : "login-required",
  });
  window.location.replace(`/login?${query.toString()}`);
}
