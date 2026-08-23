# 팀플 AI 매니저 API 명세

이 문서는 현재 `main` 브랜치에 구현된 Spring Boot 컨트롤러와 DTO를 기준으로 작성했습니다.

## 1. 공통 정보

| 항목 | 값 |
| --- | --- |
| 로컬 Base URL | `http://localhost:8080` |
| API Prefix | `/api` |
| 요청·응답 형식 | `application/json` |
| 날짜 형식 | `YYYY-MM-DD` |
| 날짜·시간 형식 | ISO 8601 문자열 |

로컬 프론트엔드는 다음 환경 변수를 사용합니다.

```env
NEXT_PUBLIC_API_URL=http://localhost:8080
```

### 대시보드 사용자 헤더

대시보드 API와 개인 업무 상태 변경 API는 현재 사용자 식별을 위해 다음 헤더가 필요합니다.

```http
X-Current-User-Id: %EB%B0%95%EA%B7%9C%EB%82%A8
```

- 값은 프로젝트 `members`에 등록된 사용자 이름 또는 ID입니다.
- 한글 이름은 URL 인코딩해서 전달합니다.
- 헤더가 없으면 `401 Unauthorized`입니다.
- 프로젝트 팀원이 아니면 프로젝트 대시보드 조회 시 `403 Forbidden`입니다.
- 로그인 기능이 연결되면 서버의 `currentUserId` 요청 속성으로 대체할 수 있습니다.

### 주요 상태값

| 구분 | 값 | 의미 |
| --- | --- | --- |
| 프로젝트 상태 | `ACTIVE` | 진행 중 |
| 프로젝트 상태 | `DISPOSAL_SCHEDULED` | 폐기 예정 |
| 프로젝트 상태 | `DISPOSED` | 폐기됨 |
| 통합 업무 상태 | `TODO` | 진행 중 |
| 통합 업무 상태 | `COMPLETED` | 완료 |
| 개인 업무 상태 | `TODO` | 진행 중 |
| 개인 업무 상태 | `DONE` | 완료 |
| 기한 상태 | `ON_TRACK` | 기한 내 |
| 기한 상태 | `OVERDUE` | 기한 초과 |
| 기한 상태 | `NO_DEADLINE` | 기한 없음 |
| 기한 상태 | `UNKNOWN_DEADLINE` | 해석할 수 없는 기한 |

## 2. API 요약

| Method | Endpoint | 설명 | 사용자 헤더 |
| --- | --- | --- | --- |
| `GET` | `/api/projects` | 프로젝트 목록 조회 | 불필요 |
| `POST` | `/api/projects` | 프로젝트 생성 | 불필요 |
| `GET` | `/api/projects/{projectId}` | 프로젝트 상세 조회 | 불필요 |
| `DELETE` | `/api/projects/{projectId}` | 프로젝트 폐기 처리 | 불필요 |
| `GET` | `/api/projects/{projectId}/minutes` | 회의록 목록 조회 | 불필요 |
| `POST` | `/api/projects/{projectId}/minutes` | AI 회의록 생성 | 불필요 |
| `GET` | `/api/projects/{projectId}/minutes/{minutesId}` | 회의록 상세 조회 | 불필요 |
| `PUT` | `/api/projects/{projectId}/minutes/{minutesId}` | 회의록 편집 | 불필요 |
| `DELETE` | `/api/projects/{projectId}/minutes/{minutesId}` | 회의록 삭제 | 불필요 |
| `GET` | `/api/projects/{projectId}/todos` | 프로젝트 통합 업무 조회 | 불필요 |
| `PATCH` | `/api/projects/{projectId}/todos/{todoId}/status` | 통합 업무 상태 변경 | 불필요 |
| `PUT` | `/api/projects/{projectId}/todos/order` | 진행 중 업무 순서 변경 | 불필요 |
| `GET` | `/api/dashboard/projects/my` | 내 프로젝트 대시보드 목록 | 필요 |
| `GET` | `/api/projects/{projectId}/dashboard/my` | 프로젝트 내 진행률 조회 | 필요 |
| `GET` | `/api/projects/{projectId}/dashboard/team` | 프로젝트 팀 진행률 조회 | 필요 |
| `PATCH` | `/api/todo-assignments/{assignmentId}` | 할당 ID로 내 진행 상태 변경 | 필요 |
| `PATCH` | `/api/todos/{todoId}/progress` | 업무 ID로 내 진행 상태 변경 | 필요 |
| `GET` | `/api/dashboard/projects` | 레거시 내 대시보드 목록 | 필요 |
| `GET` | `/api/projects/{projectId}/dashboard` | 레거시 프로젝트 대시보드 | 필요 |

## 3. 프로젝트 API

### 3.1 프로젝트 목록 조회

```http
GET /api/projects
```

응답 `200 OK`

```json
[
  {
    "id": "project-uuid",
    "name": "역사란 무엇인가",
    "members": ["박규남", "이다혜", "김민재"],
    "createdAt": "2026-08-09T14:30:00",
    "disposalDeadline": "2026-12-31",
    "status": "DISPOSAL_SCHEDULED",
    "disposedAt": null
  }
]
```

### 3.2 프로젝트 생성

```http
POST /api/projects
Content-Type: application/json
```

요청

```json
{
  "name": "역사란 무엇인가",
  "members": ["박규남", "이다혜", "김민재"],
  "disposalDeadline": "2026-12-31"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `name` | string | 필수 | 프로젝트명 또는 과목명, 공백 불가 |
| `members` | string[] | 필수 | 한 명 이상의 팀원 |
| `disposalDeadline` | date | 선택 | 프로젝트 폐기 예정일 |

응답 `200 OK`: 생성된 프로젝트 객체

### 3.3 프로젝트 상세 조회

```http
GET /api/projects/{projectId}
```

- 성공: `200 OK`
- 프로젝트 없음: `404 Not Found`

### 3.4 프로젝트 폐기

```http
DELETE /api/projects/{projectId}
```

- 성공: `204 No Content`
- 프로젝트 없음: `404 Not Found`

현재 구현은 행을 실제 삭제하지 않는 soft delete 방식입니다.

- `status`가 `DISPOSED`로 변경됩니다.
- `disposedAt`에 현재 시간이 기록됩니다.
- 기존 회의록과 업무 데이터는 유지됩니다.
- 폐기된 프로젝트에서는 새 회의록을 생성할 수 없습니다.

## 4. 회의록 API

### 4.1 회의록 목록 조회

```http
GET /api/projects/{projectId}/minutes
```

응답 `200 OK`

```json
[
  {
    "id": "minutes-uuid",
    "title": "발표 주제 선정 회의",
    "subject": "역사란 무엇인가",
    "meetingDate": "2026-08-09",
    "topic": "발표 주제와 역할 분담",
    "createdAt": "2026-08-09T15:10:00"
  }
]
```

### 4.2 AI 회의록 생성

```http
POST /api/projects/{projectId}/minutes
Content-Type: application/json
```

요청

```json
{
  "title": "",
  "meetingDate": "2026-08-09",
  "rawText": "카카오톡 대화 원문"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `title` | string | 선택 | 비어 있으면 AI 결과의 제목 사용 |
| `meetingDate` | date | 필수 | 회의 날짜 |
| `rawText` | string | 필수 | 분석할 카카오톡 대화 원문 |

생성 과정에서 다음 작업이 함께 실행됩니다.

1. Claude API로 회의록과 원문 근거 생성
2. 회의록 저장
3. 프로젝트 통합 업무 생성·동기화
4. 개인·팀 대시보드 업무와 담당자별 진행 행 생성·동기화

폐기된 프로젝트 또는 폐기 예정일이 지난 프로젝트에서는 생성을 거부합니다.

### 4.3 회의록 상세 조회

```http
GET /api/projects/{projectId}/minutes/{minutesId}
```

응답 `200 OK`

```json
{
  "id": "minutes-uuid",
  "title": "발표 주제 선정 회의",
  "topic": "발표 주제와 역할 분담",
  "discussions": ["발표 주제 후보를 비교했다"],
  "decisions": ["산업혁명을 발표 주제로 정했다"],
  "pending": ["발표 자료 디자인은 다음 회의에서 결정한다"],
  "todos": [
    {
      "name": "박규남",
      "task": "산업혁명 참고 자료 조사",
      "deadline": "2026-08-12"
    }
  ],
  "nextAgenda": ["발표 자료 디자인 확정"],
  "evidence": {
    "title": "그럼 산업혁명 주제로 정하자",
    "topic": "오늘 주제랑 역할까지 정하면 될 것 같아",
    "discussions": ["산업혁명이 자료 찾기는 더 쉬울 것 같아"],
    "decisions": ["그럼 산업혁명으로 하자"],
    "pending": ["디자인은 다음에 정하자"],
    "todos": ["규남이가 수요일까지 자료 찾아줘"],
    "nextAgenda": ["다음 회의 때 디자인 정하자"]
  }
}
```

`evidence`는 AI가 각 항목을 만들 때 참고한 카카오톡 원문 인용입니다. 기존 회의록은 `null`일 수 있습니다.

### 4.4 회의록 편집

```http
PUT /api/projects/{projectId}/minutes/{minutesId}
Content-Type: application/json
```

요청

```json
{
  "title": "수정된 회의록 제목",
  "topic": "수정된 회의 주제",
  "discussions": ["논의 내용"],
  "decisions": ["결정 사항"],
  "pending": [],
  "todos": [
    {
      "name": "이다혜",
      "task": "발표 자료 작성",
      "deadline": "2026-08-14"
    }
  ],
  "nextAgenda": ["발표 리허설"]
}
```

- 성공: `200 OK`
- 회의록 없음: `404 Not Found`
- 편집 후 통합 업무와 대시보드 업무가 다시 동기화됩니다.
- 기존 `evidence`는 유지됩니다.

### 4.5 회의록 삭제

```http
DELETE /api/projects/{projectId}/minutes/{minutesId}
```

- 성공: `204 No Content`
- 회의록 없음: `404 Not Found`
- 대시보드용 업무와 담당자별 진행 데이터도 함께 정리됩니다.

## 5. 프로젝트 통합 업무 API

통합 업무 API는 회의록에서 추출된 업무를 프로젝트 단위로 모아 완료 상태와 우선순위를 관리합니다.

### 5.1 통합 업무 조회

```http
GET /api/projects/{projectId}/todos?status=TODO
```

| Query | 값 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `status` | `TODO`, `COMPLETED` | `TODO` | 조회할 업무 상태 |

응답 `200 OK`

```json
[
  {
    "id": "integrated-todo-uuid",
    "content": "산업혁명 참고 자료 조사",
    "assignee": {
      "id": "stable-assignee-uuid",
      "name": "박규남"
    },
    "meetingNoteId": "minutes-uuid",
    "status": "TODO",
    "priorityOrder": 1,
    "dueDate": "2026-08-12",
    "completedAt": null,
    "createdAt": "2026-08-09T15:10:00",
    "updatedAt": "2026-08-09T15:10:00"
  }
]
```

### 5.2 통합 업무 상태 변경

```http
PATCH /api/projects/{projectId}/todos/{todoId}/status
Content-Type: application/json
```

요청

```json
{
  "status": "COMPLETED"
}
```

- 가능한 값: `TODO`, `COMPLETED`
- 완료 시 `completedAt`이 기록됩니다.
- `TODO`로 복원하면 `completedAt`은 `null`이 됩니다.
- 성공: `200 OK`
- 프로젝트에 속한 업무가 아님: `404 Not Found`

### 5.3 진행 중 업무 순서 변경

```http
PUT /api/projects/{projectId}/todos/order
Content-Type: application/json
```

요청

```json
{
  "orderedTodoIds": ["todo-id-2", "todo-id-1", "todo-id-3"]
}
```

주의사항:

- 빈 배열은 허용하지 않습니다.
- 해당 프로젝트의 모든 `TODO` 업무 ID를 빠짐없이 전달해야 합니다.
- 중복되거나 누락된 ID가 있으면 `400 Bad Request`입니다.
- 응답은 변경된 순서의 업무 배열입니다.

## 6. 대시보드 API

대시보드 API는 같은 회의록 업무라도 담당자별 완료 상태를 독립적으로 관리합니다.

예를 들어 담당자가 `전체`인 하나의 업무는 팀원별 할당 행이 생성되며, 한 명이 완료해도 다른 팀원의 상태는 변경되지 않습니다.

### 6.1 내 프로젝트 대시보드 목록

```http
GET /api/dashboard/projects/my
X-Current-User-Id: %EB%B0%95%EA%B7%9C%EB%82%A8
```

응답 `200 OK`

```json
[
  {
    "projectId": "project-uuid",
    "projectName": "역사란 무엇인가",
    "userId": "박규남",
    "memberName": "박규남",
    "target": "산업혁명 참고 자료 조사",
    "totalTodoCount": 2,
    "completedTodoCount": 1,
    "pendingTodoCount": 1,
    "progressRate": 50,
    "todos": []
  }
]
```

- 현재 사용자가 프로젝트 `members`에 포함된 프로젝트만 반환합니다.
- 배정된 업무가 없는 프로젝트는 목록에서 제외됩니다.

### 6.2 프로젝트 내 진행률

```http
GET /api/projects/{projectId}/dashboard/my
X-Current-User-Id: %EB%B0%95%EA%B7%9C%EB%82%A8
```

응답은 단일 `MyProjectDashboardResponse`입니다.

- 성공: `200 OK`
- 프로젝트 없음: `404 Not Found`
- 프로젝트 팀원이 아님: `403 Forbidden`

### 6.3 프로젝트 팀 진행률

```http
GET /api/projects/{projectId}/dashboard/team
X-Current-User-Id: %EB%B0%95%EA%B7%9C%EB%82%A8
```

응답 `200 OK`

```json
{
  "projectId": "project-uuid",
  "projectName": "역사란 무엇인가",
  "members": [
    {
      "userId": "박규남",
      "memberName": "박규남",
      "totalTodoCount": 2,
      "completedTodoCount": 1,
      "pendingTodoCount": 1,
      "progressRate": 50,
      "todos": []
    }
  ],
  "todos": [
    {
      "todoId": "dashboard-todo-uuid",
      "projectId": "project-uuid",
      "projectName": "역사란 무엇인가",
      "minutesId": "minutes-uuid",
      "minutesTitle": "발표 주제 선정 회의",
      "task": "발표 자료 검토",
      "deadline": "2026-08-14",
      "sourceAssignee": "전체",
      "assignments": []
    }
  ]
}
```

### 6.4 할당 ID로 내 업무 진행 상태 변경

```http
PATCH /api/todo-assignments/{assignmentId}
X-Current-User-Id: %EB%B0%95%EA%B7%9C%EB%82%A8
Content-Type: application/json
```

요청 방식 1

```json
{
  "completed": true
}
```

요청 방식 2

```json
{
  "status": "DONE"
}
```

- `status`는 `TODO` 또는 `DONE`입니다.
- 다른 사용자의 할당 행은 변경할 수 없습니다.
- 배정이 해제된 업무는 변경할 수 없습니다.
- 성공: `200 OK`
- 할당 없음: `404 Not Found`
- 권한 없음: `403 Forbidden`
- 잘못된 상태값: `400 Bad Request`

### 6.5 업무 ID로 내 진행 상태 변경

```http
PATCH /api/todos/{todoId}/progress
X-Current-User-Id: %EB%B0%95%EA%B7%9C%EB%82%A8
Content-Type: application/json
```

요청 본문은 6.4와 같습니다. 현재 사용자에게 배정된 해당 업무의 진행 행을 찾아 변경합니다.

### 개인 업무 응답 형식

```json
{
  "assignmentId": "assignment-uuid",
  "todoId": "dashboard-todo-uuid",
  "projectId": "project-uuid",
  "projectName": "역사란 무엇인가",
  "minutesId": "minutes-uuid",
  "minutesTitle": "발표 주제 선정 회의",
  "userId": "박규남",
  "memberName": "박규남",
  "task": "산업혁명 참고 자료 조사",
  "deadline": "2026-08-12",
  "assigned": true,
  "completed": false,
  "status": "TODO",
  "completedAt": null,
  "deadlineStatus": "ON_TRACK"
}
```

## 7. 레거시 대시보드 API

다음 API는 기존 클라이언트 호환용 응답을 제공합니다. 신규 화면에서는 6장의 `/my`, `/team` API 사용을 권장합니다.

### 7.1 레거시 내 대시보드 목록

```http
GET /api/dashboard/projects
X-Current-User-Id: encoded-user-id
```

개인 대시보드 응답에 다음 집계 필드가 추가된 형태입니다.

- `onTrackTodoCount`
- `overdueTodoCount`

### 7.2 레거시 프로젝트 대시보드

```http
GET /api/projects/{projectId}/dashboard
X-Current-User-Id: encoded-user-id
```

응답에는 다음 정보가 포함됩니다.

- `selectedUserId`
- `selectedMemberName`
- `selectedMember`
- `teamMembers`
- 팀원별 전체·완료·진행 중·기한 상태별 업무 수

## 8. 오류 응답과 클라이언트 처리

| 상태 코드 | 대표 조건 | 프론트 처리 권장 |
| --- | --- | --- |
| `400` | 필수 입력 누락, 잘못된 상태값, 잘못된 업무 순서 | 입력값 확인 메시지 |
| `401` | 대시보드 사용자 헤더 누락 | 현재 사용자 선택 또는 로그인 유도 |
| `403` | 프로젝트 비팀원, 다른 사용자의 업무 변경 | 권한 없음 안내 |
| `404` | 프로젝트·회의록·업무·할당 없음 | 삭제 또는 잘못된 주소 안내 |
| `500` | AI 분석, DB 연결, 처리되지 않은 서버 예외 | 재시도 및 서버 로그 확인 |

오류 응답의 `message` 필드가 있으면 이를 우선 표시하고, 없으면 HTTP 상태 코드와 프론트 기본 메시지를 함께 표시합니다.

## 9. CORS

현재 백엔드는 `/api/**`에 대해 다음 Origin을 허용합니다.

- `http://localhost:3000`
- `https://*.vercel.app`

허용 Method:

```text
GET, POST, PUT, PATCH, DELETE, OPTIONS
```
## 10. 추가 명세: 업무 ID 기준과 프로젝트 생명주기 정책

이 섹션은 프로젝트 생명주기 정리 작업에서 추가된 백엔드 구현 기준입니다. 기존 명세와 호환되는 필드는 유지합니다.

### 10.1 업무 ID 기준

| 화면/기능 | 클라이언트에 노출되는 `todoId` | 내부 저장소 |
| --- | --- | --- |
| 프로젝트 체크리스트 | `integrated_todos.id` | `integrated_todos` |
| 내 대시보드 | `integrated_todos.id`로 변환해서 응답 | `project_todos`, `todo_member_progress` |
| 팀 대시보드 | `integrated_todos.id`로 변환해서 응답 | `project_todos`, `todo_member_progress` |
| `PATCH /api/todos/{todoId}/progress` | `integrated_todos.id` 또는 기존 `project_todos.id` 둘 다 처리 | 내부에서 source 기준 매핑 |

프로젝트 체크리스트와 대시보드는 아직 내부 저장소가 분리되어 있지만, 클라이언트 응답의 `todoId`는 체크리스트 기준 ID로 맞춥니다.
체크리스트 완료/복원은 대시보드 개인 진행률에 반영되고, 대시보드 진행률 변경은 체크리스트 상태에 반영됩니다.

### 10.2 프로젝트 생명주기 정책

| 동작 | 정책 |
| --- | --- |
| 일반 목록 조회 | `DELETED` 프로젝트 제외 |
| 휴지통 이동 | `DELETE /api/projects/{projectId}` 호출 시 백엔드 내부 상태 `DELETED`, `deletedAt` 기록 |
| 복원 | `deletedAt` 제거 후 `endDate` 기준으로 상태 재계산 |
| 자동 종료 | 스케줄러가 기본 1시간마다 `endDate` 경과 프로젝트를 내부 상태 `ENDED`로 변경 |
| 회의록 생성 차단 | 내부 상태 `ENDED`, `DELETED`, 종료일 경과 상태에서 차단 |
| 영구 삭제 | 프로젝트 관련 업무/회의록/진행률 삭제 후 프로젝트 row 삭제 |

### 10.3 프로젝트 응답 호환 필드

| 응답 필드 | 값 기준 | 비고 |
| --- | --- | --- |
| `endDate` | `project.endDate` | 백엔드 신규 표준 필드 |
| `disposalDeadline` | `project.endDate` | 기존 프론트 호환 필드 |
| `status` | `ProjectStatus.toClientStatus()` | 기존 프론트 호환 상태값 유지 |
| `endedAt` | `project.endedAt` | 백엔드 신규 표준 필드 |
| `disposedAt` | `project.endedAt` | 기존 프론트 호환 필드 |
| `deletedAt` | `project.deletedAt` | 사용자 삭제 시각 |

상세 백엔드 공용 메서드 기준은 `docs/BACKEND_PROJECT_LIFECYCLE.md`를 참고합니다.
## 11. JWT 인증 및 계정 기반 팀원 API

이 섹션은 `feat/minjae/JWT` 작업에서 추가된 백엔드 인증 기준입니다. 기존 프론트 호환 필드와 레거시 `X-Current-User-Id` 흐름은 즉시 제거하지 않고 유지합니다.

### 11.1 Supabase JWT 인증

보호된 API는 다음 헤더를 사용합니다.

```http
Authorization: Bearer <Supabase Access Token>
```

백엔드는 Supabase API를 매 요청마다 호출하지 않고, Supabase JWKS 공개키 엔드포인트로 받은 공개키를 캐시해 JWT를 직접 검증합니다.
현재 Supabase 프로젝트는 새 JWT Signing Keys를 사용하므로 `SUPABASE_JWT_SECRET` 기반 Legacy HS256 검증을 사용하지 않습니다.

검증 기준:

- JWT header `alg`는 `ES256`이어야 합니다.
- JWT header `kid`는 필수이며, JWKS의 공개키 `kid`와 매칭되어야 합니다.
- JWKS endpoint: `https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json`
- JWT signature를 JWKS의 P-256 공개키로 검증합니다.
- JWT payload의 `exp` 만료 시간을 검증합니다.
- `SUPABASE_JWT_ISSUER`가 설정되어 있으면 JWT payload의 `iss`와 일치해야 합니다.
- JWT payload의 `sub`를 실제 로그인 사용자 ID로 사용합니다.
- JWT payload의 `email`을 사용자 이메일로 사용합니다.
- JWT payload의 `user_metadata.name`, `user_metadata.full_name`, `user_metadata.display_name` 중 첫 번째 값을 표시 이름 후보로 사용합니다.
- 표시 이름 후보가 없으면 email prefix를 사용하고, email이 없으면 `sub`를 사용합니다.
- JWT 원문과 초대/관리자 인증 값은 로그에 출력하지 않습니다.

백엔드 `.env` 필요 값:

```env
SUPABASE_JWKS_URI=https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json
SUPABASE_JWT_ISSUER=https://<project-ref>.supabase.co/auth/v1
```

오류:

| 상태 코드 | 조건 |
| --- | --- |
| `401` | Authorization Bearer 토큰 없음 |
| `401` | JWT 형식 오류 |
| `401` | JWT header `alg`가 `ES256`이 아님 |
| `401` | JWT header `kid` 누락 또는 JWKS에서 매칭되는 공개키 없음 |
| `401` | JWT 서명 검증 실패 |
| `401` | JWT 만료 |
| `401` | JWT issuer 불일치 |
| `401` | `SUPABASE_JWKS_URI` 미설정 |
### 11.2 현재 사용자 해석 기준

일반 로그인 사용자의 기준 ID는 Supabase JWT의 `sub`입니다.

```text
authUserId = JWT sub
email = JWT email
memberKey = user_metadata 표시 이름 -> email prefix -> sub
```

현재 전환 기간에는 기존 대시보드/업무 데이터가 문자열 담당자 이름을 사용하므로, 일부 레거시 API는 `memberKey`를 `currentUserId`로 전달받아 기존 로직과 호환됩니다. 신규 계정 기반 권한과 초대/협업 기능은 `authUserId` 기준으로 확장합니다.

### 11.3 관리자 테스트 인증

프론트의 사용자 전환 테스트를 위해 임시 관리자 인증을 제공합니다. 운영용 인증이 아니며 개발/테스트용입니다.

기본값:

```env
ADMIN_TEST_ID=admin
ADMIN_TEST_PASSWORD=1234
```

헤더 방식:

```http
X-Admin-Id: admin
X-Admin-Password: 1234
X-Current-User-Id: member-a
```

Basic Auth 방식:

```http
Authorization: Basic Base64(admin:1234)
X-Current-User-Id: member-a
```

관리자 테스트 인증에서는 `X-Current-User-Id`로 선택한 사용자를 `currentUserId`로 사용합니다. 값이 없으면 관리자 ID를 사용합니다.

오류:

| 상태 코드 | 조건 |
| --- | --- |
| `401` | 관리자 ID 또는 비밀번호 불일치 |
| `401` | Basic Auth 형식 오류 |

### 11.4 인증 확인 API

```http
GET /api/auth/me
Authorization: Bearer <Supabase Access Token>
```

응답 `200 OK`

```json
{
  "authUserId": "supabase-user-id",
  "memberKey": "김민재",
  "email": "minjae@example.com",
  "authMode": "SUPABASE"
}
```

관리자 테스트 인증으로 호출하면 `authMode`는 `ADMIN_TEST`입니다.

### 11.5 관리자 테스트 로그인 확인 API

```http
POST /api/auth/admin/verify
Content-Type: application/json

{
  "adminId": "admin",
  "password": "1234"
}
```

응답 `200 OK`

```json
{
  "admin": true,
  "adminId": "admin",
  "authMode": "ADMIN_TEST"
}
```

오류:

| 상태 코드 | 조건 |
| --- | --- |
| `401` | 관리자 ID 또는 비밀번호 불일치 |

### 11.6 프로젝트 팀원 조회 API

```http
GET /api/projects/{projectId}/members
Authorization: Bearer <Supabase Access Token>
```

프로젝트 회원만 조회할 수 있습니다. 관리자 테스트 인증은 테스트 목적으로 조회할 수 있습니다.

응답 `200 OK`

```json
[
  {
    "userId": "supabase-user-id-1",
    "displayName": "김민재",
    "role": "OWNER",
    "joinedAt": "2026-08-22T00:00:00"
  },
  {
    "userId": "supabase-user-id-2",
    "displayName": "이다혜",
    "role": "MEMBER",
    "joinedAt": "2026-08-22T00:10:00"
  }
]
```

오류:

| 상태 코드 | 조건 |
| --- | --- |
| `401` | 인증되지 않음 |
| `403` | 프로젝트 회원이 아님 |
| `404` | 프로젝트 없음 |

### 11.7 프로젝트 생성 시 OWNER 등록

```http
POST /api/projects
Authorization: Bearer <Supabase Access Token>
Content-Type: application/json
```

프로젝트 생성 성공 시 JWT의 `sub` 사용자가 `project_members`에 `OWNER`로 자동 등록됩니다.

기존 `ProjectRequest.members`는 프론트/회의록/업무 담당자 호환을 위해 유지합니다. 초대/협업이 완성되기 전까지 제거하지 않습니다.
### 11.8 JWT 적용 보호 API

다음 API는 인증 필터를 통과해야 하며, 컨트롤러에서 프로젝트 회원 또는 OWNER 권한을 확인합니다.

| Method | Endpoint | 권한 기준 |
| --- | --- | --- |
| `POST` | `/api/projects` | 로그인 사용자 필요. 생성자는 `project_members`에 `OWNER`로 등록 |
| `GET` | `/api/projects/{projectId}` | 프로젝트 회원 또는 관리자 테스트 인증 |
| `DELETE` | `/api/projects/{projectId}` | 프로젝트 `OWNER` 또는 관리자 테스트 인증 |
| `PATCH` | `/api/projects/{projectId}/restore` | 프로젝트 `OWNER` 또는 관리자 테스트 인증 |
| `DELETE` | `/api/projects/{projectId}/permanent` | 프로젝트 `OWNER` 또는 관리자 테스트 인증 |
| `GET` | `/api/projects/{projectId}/members` | 프로젝트 회원 또는 관리자 테스트 인증 |
| `GET` | `/api/projects/{projectId}/minutes` | 프로젝트 회원 또는 관리자 테스트 인증 |
| `POST` | `/api/projects/{projectId}/minutes` | 프로젝트 회원 또는 관리자 테스트 인증 |
| `GET` | `/api/projects/{projectId}/minutes/{minutesId}` | 프로젝트 회원 또는 관리자 테스트 인증. `minutesId`가 해당 프로젝트 소속이어야 함 |
| `PUT` | `/api/projects/{projectId}/minutes/{minutesId}` | 프로젝트 회원 또는 관리자 테스트 인증. `minutesId`가 해당 프로젝트 소속이어야 함 |
| `DELETE` | `/api/projects/{projectId}/minutes/{minutesId}` | 프로젝트 회원 또는 관리자 테스트 인증. `minutesId`가 해당 프로젝트 소속이어야 함 |
| `GET` | `/api/projects/{projectId}/todos` | 프로젝트 회원 또는 관리자 테스트 인증 |
| `PATCH` | `/api/projects/{projectId}/todos/{todoId}/status` | 프로젝트 회원 또는 관리자 테스트 인증 |
| `PUT` | `/api/projects/{projectId}/todos/order` | 프로젝트 회원 또는 관리자 테스트 인증 |

전환기 호환 규칙:

- 신규 계정 기반 권한은 `project_members.user_id = JWT sub`를 우선 사용합니다.
- 기존 프로젝트 중 `project_members` 행이 아직 없는 프로젝트는 `Project.members` 문자열과 로그인 사용자의 `authUserId`, `memberKey`, `email` 중 하나가 일치하면 임시로 프로젝트 회원으로 인정합니다.
- `project_members` 행이 하나라도 있는 프로젝트는 레거시 `Project.members`만으로 권한을 인정하지 않습니다.
- 이 호환 규칙은 기존 데이터 마이그레이션이 끝날 때 제거할 수 있습니다.
### 11.9 대시보드 JWT 권한 기준

이번 단계에서는 Spring Security `SecurityFilterChain` 전환 대신 기존 커스텀 `OncePerRequestFilter` 기반 인증 필터를 유지합니다.

유지 이유:

- 기존 API/CORS/테스트 흐름 변경 범위를 줄입니다.
- 초대 코드와 프론트 인증 전환이 끝나기 전까지 401/403 동작 변화를 최소화합니다.
- JWT 직접 검증과 요청 사용자 해석은 커스텀 필터 안에서 처리합니다.

대시보드 API는 다음처럼 사용자 기준을 분리합니다.

| 구분 | 기준 |
| --- | --- |
| 프로젝트 접근 권한 | `project_members.user_id = JWT sub` 우선 |
| 기존 데이터 호환 권한 | `project_members`가 비어 있으면 `Project.members`와 `authUserId`, `memberKey`, `email` 중 하나 일치 |
| 개인 진행률 조회/수정 | 기존 `todo_member_progress.user_id`와 JWT에서 해석한 `memberKey` 일치 |
| 관리자 테스트 인증 | `X-Current-User-Id`로 선택한 사용자를 `memberKey`처럼 사용 |

즉, 로그인 사용자의 실제 계정 권한은 `JWT sub`로 확인하고, 기존 Todo 진행률 데이터는 아직 문자열 담당자 이름 기반이므로 `memberKey`로 조회합니다.

보호되는 대시보드 API:

| Method | Endpoint | 처리 기준 |
| --- | --- | --- |
| `GET` | `/api/dashboard/projects` | JWT 사용자 또는 관리자 테스트 인증 기준 프로젝트 접근 확인 |
| `GET` | `/api/dashboard/projects/my` | JWT 사용자 또는 관리자 테스트 인증 기준 프로젝트 접근 확인 |
| `GET` | `/api/projects/{projectId}/dashboard` | 프로젝트 회원 또는 관리자 테스트 인증 |
| `GET` | `/api/projects/{projectId}/dashboard/my` | 프로젝트 회원 또는 관리자 테스트 인증 |
| `GET` | `/api/projects/{projectId}/dashboard/team` | 프로젝트 회원 또는 관리자 테스트 인증 |
| `PATCH` | `/api/todo-assignments/{assignmentId}` | 프로젝트 회원 확인 후 본인 담당 진행률만 수정 |
| `PATCH` | `/api/todos/{todoId}/progress` | 프로젝트 회원 확인 후 본인 담당 진행률만 수정 |

팀 대시보드의 `members` 초기화 기준:

- `project_members`가 있으면 `project_members.display_name`을 우선 사용합니다.
- `project_members`가 없으면 기존 `Project.members`를 사용합니다.
- 실제 진행률 행이 있는 사용자는 초기 목록에 없어도 응답에 포함됩니다.
### 11.10 프로젝트 목록 JWT 전환

`GET /api/projects`와 `GET /api/projects/trash`는 이제 인증 필수 API입니다.

```http
GET /api/projects
Authorization: Bearer <Supabase Access Token>
```

```http
GET /api/projects/trash
Authorization: Bearer <Supabase Access Token>
```

처리 기준:

- 일반 사용자는 접근 가능한 프로젝트만 반환합니다.
- 접근 가능 여부는 `project_members.user_id = JWT sub`를 우선 사용합니다.
- `project_members`가 아직 없는 기존 프로젝트는 전환기 호환 규칙에 따라 `Project.members`와 로그인 사용자의 `authUserId`, `memberKey`, `email` 중 하나가 일치하면 반환합니다.
- 관리자 테스트 인증은 모든 프로젝트 목록을 볼 수 있습니다.
- `GET /api/projects`는 `DELETED` 프로젝트를 제외합니다.
- `GET /api/projects/trash`는 `DELETED` 프로젝트만 반환합니다.

오류:

| 상태 코드 | 조건 |
| --- | --- |
| `401` | 인증 헤더 없음 또는 JWT 검증 실패 |
