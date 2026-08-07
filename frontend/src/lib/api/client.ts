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

export async function apiRequest<T>(
  path: string,
  { errorMessage, headers, ...options }: ApiRequestOptions
): Promise<T> {
  if (!apiUrl) {
    throw new ApiError("백엔드 API 주소가 설정되지 않았습니다.", 0);
  }

  const requestHeaders = new Headers(headers);
  if (options.body && !requestHeaders.has("Content-Type")) {
    requestHeaders.set("Content-Type", "application/json");
  }

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
    throw new ApiError("서버 응답 형식이 올바르지 않습니다.", response.status);
  }
}
