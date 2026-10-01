export class TimeoutError extends Error {
  constructor(message = "요청 시간이 초과되었습니다.") {
    super(message);
    this.name = "TimeoutError";
  }
}

/**
 * promise가 ms 안에 끝나지 않으면 TimeoutError로 거부한다.
 * Supabase 세션 조회처럼 네트워크가 막히면 영영 끝나지 않는 호출을 감쌀 때 사용한다.
 */
export function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<never>((_, reject) => {
    timer = setTimeout(() => reject(new TimeoutError()), ms);
  });

  return Promise.race([promise, timeout]).finally(() => {
    if (timer !== undefined) clearTimeout(timer);
  });
}
