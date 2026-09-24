# 출시 설정 및 검증

## 변경 내용

- 관리자 테스트 인증은 기본 꺼짐. `dev`에서만 `ADMIN_TEST_ENABLED=true`, 명시적인 ID와 16자 이상 비밀번호로 활성화 가능. `prod`에서는 무조건 거부.
- 모든 `/api/**`에 기본 인증 적용. 유일한 예외인 테스트 로그인도 위 개발 환경 제한 적용.
- CORS는 `CORS_ALLOWED_ORIGINS`에 지정한 정확한 origin만 허용. 인증 오류 응답에도 CORS 헤더 제공.
- 프로젝트 권한과 개인 업무는 Supabase JWT `sub`로 구분. 표시 이름으로 권한을 부여하지 않음.
- AI와 팀 업무는 `project_members`의 실제 가입자 목록 사용. 이름이 중복되어 한 사람을 특정할 수 없는 업무는 미배정 처리. 담당자 입력에 계정 UUID를 사용하면 명시적으로 배정 가능. `all`/`전체`는 각 계정에 별도 배정.
- 회의록 업무의 `sourceIndex`는 변경되지 않는 식별자. 편집 시 반환받은 값을 유지하고 새 항목은 생략. 삭제된 ID는 재사용하지 않음. 삭제 항목의 보드·대시보드 행도 정리.
- AI 호출은 DB 트랜잭션 밖에서 수행. 저장 전 프로젝트 상태와 권한을 다시 확인.
- 입력 50,000자, 사용자별 최근 24시간 20회, 사용자별 동시 1회, 서비스 전체 동시 4회가 기본값. DB에서 예약하므로 복수 인스턴스에서도 적용.
- 생성 시 `Idempotency-Key` 필요(16~100자 영문/숫자/`_`/`-`). 같은 키·내용의 성공 요청은 기존 회의록 반환. 처리 중 409, 실패했던 요청 410, 제한 초과 429. 실제 AI를 시도한 실패도 사용량에 포함.
- AI 타임아웃 90초, 자동 재시도 없음. 잘린 출력·빈 출력·잘못된 결과는 502. 서버 비정상 종료로 남은 예약은 5분 후 정리.
- 인증 키 조회 연결 3초/전체 5초 제한. 공개 키 캐시 10분, 갱신 시도 최소 간격 30초.
- Spring Boot 4용 springdoc 3.x 사용. 운영 Swagger는 기본 비활성화.

## 운영 환경변수

백엔드(Railway 등)에 다음을 설정합니다. `.env` 파일은 `bootRun` 로컬 실행에서만 자동으로 읽습니다. 배포 JAR에는 플랫폼 환경변수를 주입하세요.

| 변수 | 값 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` (기본값도 prod) |
| `DATABASE_URL` | `jdbc:postgresql://HOST:PORT/DB` — 플랫폼이 제공하는 `postgres://` URL을 그대로 넣지 않음 |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | 실제 DB 자격 증명 |
| `ANTHROPIC_API_KEY` | 서버 전용 키 |
| `SUPABASE_JWKS_URI` | `https://PROJECT.supabase.co/auth/v1/.well-known/jwks.json` |
| `SUPABASE_JWT_ISSUER` | `https://PROJECT.supabase.co/auth/v1` |
| `CORS_ALLOWED_ORIGINS` | `https://실제-프런트엔드-도메인` — 끝 슬래시 없음. 여러 개는 쉼표 구분 |
| `AI_DAILY_LIMIT` / `AI_CONCURRENT_LIMIT` | 선택. 기본값 20 / 4 |
| `PORT` | 플랫폼 지정 포트 |

프런트엔드는 **빌드 전에** `NEXT_PUBLIC_API_URL`, `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY`를 설정합니다.
Supabase 공개 키만 사용하고 service-role 키는 넣지 않습니다. 변수 누락은 빌드 시작 시 이름을 표시하며 실패합니다.
운영 API URL은 HTTPS를 사용합니다. 로컬 검증만 localhost HTTP를 허용합니다.
Supabase 설정에서 Google/카카오 provider와 운영 `/login` 리디렉션 URL을 별도로 등록해야 합니다.
현재 JWT 검증은 ES256 서명 키를 사용합니다.

## 기존 DB 전환

1. 운영 DB를 백업하고 복원 가능 여부를 확인합니다. 실제 운영 DB 변경은 이 작업에서 수행하지 않았습니다.
2. 기존 `projects` 중 `project_members`가 없는 행을 조회합니다.

   ```sql
   SELECT p.id, p.name FROM projects p
   WHERE NOT EXISTS (SELECT 1 FROM project_members pm WHERE pm.project_id = p.id);
   ```

3. 소유자와 팀원의 실제 Supabase 사용자 UUID를 확인해 `project_members`에 등록합니다. 이름이나 이메일 앞부분만 보고 계정을 추정하지 않습니다. 각 프로젝트에 실제 소유자를 `OWNER`로 등록해야 합니다. 이 매핑은 운영 데이터가 없으므로 자동 생성할 수 없습니다.
4. 매핑하지 않은 과거 프로젝트는 접근이 거부됩니다. 신규 프로젝트는 생성자 UUID가 자동 등록됩니다.
5. 기존 이름 기반 진척도는 가입자 이름이 하나의 계정에만 대응할 때 동기화 과정에서 완료 상태를 유지한 채 UUID로 전환합니다. 동명이인 데이터는 담당자를 확인해 다시 배정합니다.
6. Flyway `V20260925`가 AI 요청 기록·동시 요청 잠금 테이블과 회의록 업무 ID 카운터를 추가합니다. 이후 Hibernate는 `validate`만 실행합니다. 기존 Flyway 파일은 수정하지 않습니다.
7. 애플리케이션과 프런트엔드를 함께 배포합니다. 이전 프런트엔드는 생성 요청 키와 업무 ID를 보내지 않습니다.

## 검증

```powershell
cd backend
.\gradlew.bat test
cd ../frontend
npm.cmd run lint
npm.cmd run build
```

통합 테스트는 H2에 실제 Flyway 마이그레이션과 스키마 검증을 실행합니다. AI만 대체하여 다음을 검증합니다.

- 두 계정 프로젝트 생성·초대·AI 팀원 전달·업무 배정·본인만 진척도 수정
- 동명이인 분리와 팀 전체 배정
- 완료된 앞 항목 삭제 후 다른 항목의 완료 상태·ID 유지
- 생성 재시도 중복 방지, 입력 날짜 선검증, AI 실패 시 저장 취소와 예약 해제
- 최근 24시간 한도와 여러 스레드의 전역 동시 요청 제한
- 삭제 프로젝트 초대 차단, 활성 초대의 소유자 전용 조회
- CORS preflight, 허용되지 않은 도메인 차단, 기본 관리자 인증 거부, OpenAPI 엔드포인트
- 운영 설정 누락/임시 DB/와일드카드 origin 차단, 공개 키 반복 조회 제한

별도로 실제 배포 환경에서 PostgreSQL 마이그레이션, 두 실제 계정의 소셜 로그인,
유료 AI 한 번 호출, HTTPS 도메인 간 통신을 확인해야 합니다. 자동 테스트는 실제 제공자 계정이나 운영 DB를 호출하지 않습니다.
