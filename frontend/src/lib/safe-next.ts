const DEFAULT_NEXT_PATH = "/projects";

/**
 * 로그인 후 이동할 `next` 값을 같은 사이트 내 경로로만 제한한다.
 * `//evil.com`, `/\evil.com` 같은 프로토콜 상대 URL은 외부 리다이렉트가 되므로 거부한다.
 */
export function getSafeNextPath(value: string | null | undefined) {
  if (typeof value === "string" && /^\/(?![/\\])/.test(value)) {
    return value;
  }
  return DEFAULT_NEXT_PATH;
}
