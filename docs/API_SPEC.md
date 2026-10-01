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
| 프로젝트 상태 | `DISPOSAL_SCHEDULED` | 종료 예정일이 7일 이내 |
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

백엔드는 `/api/**`에 대해 `CORS_ALLOWED_ORIGINS`(쉼표 구분, 정확한 Origin만, 와일드카드 없음)에 적힌 Origin만 허용합니다. 기본값은 `http://localhost:3000` 하나이며, 배포한 프론트 도메인은 직접 추가해야 합니다.

```env
CORS_ALLOWED_ORIGINS=http://localhost:3000,https://teample.example.com
```

CORS 처리는 서블릿 필터(최우선 순위)로도 등록되어 있어 인증 필터가 `401`을 먼저 돌려보내도 브라우저에 CORS 헤더가 함께 전달됩니다(`Failed to fetch`가 아니라 실제 401 메시지를 볼 수 있음).

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

**기본은 비활성**입니다. 사용하려면 세 값을 모두 넣어야 하며, 하나라도 빠지면(또는 비밀번호가 12자 미만이면) 어떤 자격 증명도 통과하지 않습니다. ID/비밀번호의 기본값은 없습니다.

```env
ADMIN_TEST_ENABLED=true
ADMIN_TEST_ID=admin
ADMIN_TEST_PASSWORD=<12자 이상>
```

헤더 방식:

```http
X-Admin-Id: admin
X-Admin-Password: <ADMIN_TEST_PASSWORD>
X-Current-User-Id: member-a
```

Basic Auth 방식:

```http
Authorization: Basic Base64(admin:<ADMIN_TEST_PASSWORD>)
X-Current-User-Id: member-a
```

관리자 테스트 인증에서는 `X-Current-User-Id`로 선택한 사용자를 `currentUserId`로 사용합니다. 값이 없으면 관리자 ID를 사용합니다. 값은 URL 인코딩할 수 있으며, 잘못된 인코딩(`%E0%A4%A` 같은 값)은 `401`입니다.

오류:

| 상태 코드 | 조건 |
| --- | --- |
| `401` | 관리자 인증이 비활성(`ADMIN_TEST_ENABLED=false`)이거나 ID/비밀번호 불일치 |
| `401` | Basic Auth 형식 오류, `X-Current-User-Id` 인코딩 오류 |

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
  "id": "admin",
  "password": "<ADMIN_TEST_PASSWORD>"
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
| `404` | 관리자 테스트 인증이 비활성(`ADMIN_TEST_ENABLED=false`, 기본값). 엔드포인트가 없는 것처럼 보입니다 |
| `401` | 관리자 ID 또는 비밀번호 불일치 |
| `429` | 같은 클라이언트 IP에서 10분 안에 5회 이상 실패. 10분이 지나면 다시 시도할 수 있습니다 |

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

새 프로젝트 생성 시 `Project.members`는 빈 목록으로 저장되며, JWT의 `sub` 사용자만 `project_members`의 `OWNER`로 등록됩니다. 기존 프로젝트의 `Project.members` 데이터는 레거시 호환을 위해 유지됩니다.

### 11.8 프로젝트 초대

#### 초대 코드 생성

```http
POST /api/projects/{projectId}/invitations
Authorization: Bearer <Supabase Access Token>
```

프로젝트 OWNER만 호출할 수 있습니다. 기존 활성 코드는 비활성화되고, 72시간 후 만료되는 새 12자리 코드가 발급됩니다.

응답 `200 OK`:

```json
{
  "code": "A7KD92QM4X8P",
  "expiresAt": "2026-08-29T12:00:00"
}
```

오류: `401` 인증 필요, `403` OWNER 아님, `404` 프로젝트 없음

#### 초대 코드로 참여

```http
POST /api/project-invitations/join
Authorization: Bearer <Supabase Access Token>
Content-Type: application/json
```

```json
{ "code": "A7KD92QM4X8P" }
```

유효한 코드이면 JWT `sub` 사용자를 `project_members`에 `MEMBER`로 등록하고 프로젝트 정보를 반환합니다. `Project.members` 문자열에는 추가하지 않습니다.

응답 `200 OK`:

```json
{
  "projectId": "project-id",
  "projectName": "AI 캡스톤디자인",
  "role": "MEMBER"
}
```

오류: `401` 인증 필요, `404` 코드 또는 프로젝트 없음, `409` 이미 참여 중(동시에 두 번 참여해 유니크 제약에 걸린 경우 포함) 또는 종료·삭제된 프로젝트, `410` 만료 또는 비활성 코드

### 11.9 JWT 적용 보호 API

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
### 11.10 대시보드 JWT 권한 기준

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
### 11.11 프로젝트 목록 JWT 전환

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

## 12. 프리미엄: 요금제와 음성·영상 회의록

결제 연동 전 단계입니다. 요금제는 관리자가 수동으로 부여하며, 음성·영상 회의록은 `PREMIUM` 사용자만 사용할 수 있습니다.

### 12.1 요금제 조회

```http
GET /api/me/plan
Authorization: Bearer <Supabase Access Token>
```

```json
{
  "plan": "PREMIUM",
  "premium": true,
  "expiresAt": null,
  "features": { "transcription": true },
  "usage": { "monthMinutesUsed": 42, "monthMinutesLimit": 600 }
}
```

- `user_plans` 행이 없거나 `expiresAt`이 지났으면 `FREE`입니다.
- 관리자 테스트 인증은 항상 `PREMIUM`으로 취급합니다.
- `usage`는 이번 달 1일 이후 실패하지 않은 전사의 길이 합계(분, 올림)입니다. 한도는 `PREMIUM_MONTHLY_MINUTES`(기본 600)입니다.

### 12.2 요금제 수동 부여 (관리자)

```http
PUT /api/admin/users/{userId}/plan
Authorization: Bearer <Supabase Access Token>
Content-Type: application/json

{ "plan": "PREMIUM", "expiresAt": "2026-12-31T23:59:59", "note": "베타 테스터" }
```

- `userId`는 Supabase JWT `sub`입니다. 사용자는 프로필 화면의 "계정 ID"에서 확인할 수 있습니다.
- 호출 권한: 관리자 테스트 인증, 또는 `PLAN_ADMIN_USER_IDS`(쉼표 구분)에 포함된 사용자. 그 외는 `403`.
- `GET /api/admin/users/plans`로 부여 목록을 조회합니다.

### 12.3 음성·영상 전사

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `POST` | `/api/projects/{projectId}/transcriptions` | 파일 업로드 후 비동기 전사 시작 (`202`) |
| `GET` | `/api/projects/{projectId}/transcriptions` | 프로젝트의 전사 목록 |
| `GET` | `/api/projects/{projectId}/transcriptions/{id}` | 전사 상태·세그먼트 조회 (폴링용) |
| `PUT` | `/api/projects/{projectId}/transcriptions/{id}/speakers` | 화자 라벨 → 이름 매핑 저장 |
| `POST` | `/api/projects/{projectId}/transcriptions/{id}/minutes` | 전사로 회의록 생성 (전사당 1회) |
| `GET` | `/api/projects/{projectId}/transcriptions/{id}/audio` | 원본 녹음 파일 스트리밍 |
| `DELETE` | `/api/projects/{projectId}/transcriptions/{id}` | 전사와 파일 삭제 (처리 중이면 `409`) |

모두 프로젝트 회원 인증이 필요합니다.

업로드 요청은 `multipart/form-data`입니다.

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `file` | O | mp3, m4a, wav, flac, aac, ogg, mp4, mov, mkv, webm. 최대 `MEDIA_MAX_FILE_SIZE_MB`(기본 500MB) |
| `consent` | O | `true`여야 함. 회의 참여자 녹음 동의 확인 |
| `expectedSpeakers` | X | 예상 화자 수 0~20. 비우면 STT가 자동 추정 |
| `language` | X | 기본 `ko` |

업로드 오류:

| 상태 코드 | 조건 |
| --- | --- |
| `400` | 파일 없음, 동의 없음, 지원하지 않는 형식, 화자 수 범위 초과 |
| `403` | 프로젝트 회원이 아니거나 `FREE` 요금제 |
| `409` | 종료·삭제된 프로젝트, 또는 같은 사용자의 전사가 이미 처리 중 |
| `413` | 파일 크기 초과 |
| `429` | 이번 달 전사 사용량 초과 |
| `503` | STT 자격 증명(`RTZR_CLIENT_ID`/`RTZR_CLIENT_SECRET`) 미설정 |

전사 응답:

```json
{
  "id": "…",
  "projectId": "…",
  "status": "COMPLETED",
  "sourceFileName": "회의.m4a",
  "mediaKind": "AUDIO",
  "durationMs": 1830000,
  "expectedSpeakers": 4,
  "speakerLabels": ["0", "1", "2"],
  "speakerNames": { "0": "박규남", "1": "이다혜" },
  "segments": [
    { "speaker": "0", "startMs": 0, "endMs": 1500, "text": "오늘 회의 시작할게요" }
  ],
  "errorMessage": null,
  "minutesId": null,
  "hasAudio": true,
  "createdAt": "2026-10-01T00:00:00",
  "completedAt": "2026-10-01T00:03:12"
}
```

- `status`: `QUEUED` → `PROCESSING` → `COMPLETED` 또는 `FAILED`. 클라이언트는 4~5초 간격으로 조회합니다.
- 서버가 재시작되면 끝나지 않은 작업을 자동으로 이어서 처리합니다.
- `speakerLabels`는 STT가 부여한 익명 라벨입니다. 이름 매핑은 `PUT …/speakers`로 저장하고, 비어 있는 라벨은 회의록 생성 시 `화자 N`으로 표시됩니다.
- webm, mov, mkv는 서버에 ffmpeg가 있어야 처리됩니다. 없으면 `FAILED`와 안내 메시지가 기록됩니다.
- STT 상태 조회가 일시적으로 실패해도 연속 5회까지는 계속 기다립니다. 서버 종료로 처리가 끊기면 `FAILED`가 아니라 `PROCESSING`으로 남고 재시작 때 이어갑니다. 실행 대기열에 들어가지 못한 `QUEUED` 작업은 5분마다 다시 넣습니다.

`GET …/{id}/audio` 응답:

- `Content-Type`은 업로드 때 보낸 값을 그대로 돌려주지 않고, 서버에 저장된 파일 확장자로만 정합니다. 허용 목록: mp3→`audio/mpeg`, m4a/mp4→`audio/mp4`, wav→`audio/wav`, flac→`audio/flac`, ogg/oga/opus→`audio/ogg`, webm→`audio/webm`, aac→`audio/aac`, amr→`audio/amr`, mov→`video/quicktime`, mkv→`video/x-matroska`, avi→`video/x-msvideo`, m4v→`video/mp4`. 목록에 없으면 `application/octet-stream`.
- `X-Content-Type-Options: nosniff`, `Accept-Ranges: bytes`, `Content-Disposition: inline; filename*=UTF-8''…`가 함께 내려갑니다.

회의록 생성:

```http
POST /api/projects/{projectId}/transcriptions/{id}/minutes
Content-Type: application/json

{ "title": "", "meetingDate": "2026-10-01" }
```

- 전사 세그먼트를 `[mm:ss] 이름: 발언` 줄로 합쳐 기존 회의록 파이프라인(Claude 분석, 업무 동기화)에 넣습니다.
- 응답은 기존 `MinutesResponse`이며 `transcriptionId`가 채워집니다. 회의록 상세 화면은 이 값으로 녹음 재생과 발언 위치 이동을 제공합니다.
- 전사가 `COMPLETED`가 아니면 `409`, 이미 회의록이 있으면 `409`, 인식된 발언이 없으면 `422`.

### 12.4 환경 변수

| 변수 | 설명 |
| --- | --- |
| `RTZR_CLIENT_ID`, `RTZR_CLIENT_SECRET` | 리턴제로 STT OpenAPI 자격 증명. 없으면 전사 업로드가 `503` |
| `RTZR_MODEL_NAME` | 기본 `sommers` |
| `PLAN_ADMIN_USER_IDS` | 요금제를 부여할 수 있는 Supabase 사용자 ID 목록(쉼표) |
| `PREMIUM_MONTHLY_MINUTES` | 월 전사 한도(분). 기본 600 |
| `MEDIA_DIR` | 업로드 파일 저장 경로. 기본 `./data/media` |
| `MEDIA_MAX_FILE_SIZE_MB` | 업로드 최대 크기. 기본 500 |
| `FFMPEG_PATH`, `FFPROBE_PATH` | 선택. webm 등 변환용 |
| `STT_POLL_INTERVAL_SECONDS`, `STT_MAX_WAIT_MINUTES` | 폴링 간격(기본 5초)과 최대 대기(기본 120분) |
| `ADMIN_TEST_ENABLED`, `ADMIN_TEST_ID`, `ADMIN_TEST_PASSWORD` | 개발용 관리자 테스트 인증(11.3). 기본 비활성, 비밀번호 12자 이상 |
| `CORS_ALLOWED_ORIGINS` | 허용 Origin 목록(쉼표, 정확한 값). 기본 `http://localhost:3000` |

## 13. 프리미엄 구독 결제와 카카오톡 알림

사업자 등록 전 단계이므로 토스페이먼츠는 **문서용/개발자센터 테스트 키**로, 카카오는 **카카오 디벨로퍼스 개인 앱**으로 동작합니다. 둘 다 사업자 등록 없이 무료입니다. 실제 출금이나 알림톡 발송은 일어나지 않고, 테스트 결제와 "나에게 보내기" 메시지만 사용합니다.

### 13.1 구독 결제 (토스페이먼츠 빌링)

흐름: `POST /checkout` → 프론트가 토스 SDK `payment.requestBillingAuth({ method: "CARD", successUrl, failUrl })` 호출 → 토스가 `successUrl?customerKey=&authKey=`로 리다이렉트 → `POST /confirm` → 백엔드가 빌링키 발급 후 첫 달 결제 → 30일마다 자동 결제.

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/me/subscription` | 내 구독. 없으면 `204` |
| `POST` | `/api/me/subscription/checkout` | SDK 초기화 값 `{ clientKey, customerKey, amount, orderName, customerEmail, customerName, testMode }` |
| `POST` | `/api/me/subscription/confirm` | `{ authKey, customerKey }` → 빌링키 발급 + 첫 결제 + 프리미엄 부여 |
| `POST` | `/api/me/subscription/cancel` | 해지. 현재 기간 종료까지 프리미엄 유지 |
| `GET` | `/api/me/subscription/payments` | 최근 결제 내역 20건 |

- `customerKey`는 `tpl-<Supabase sub>`로 고정되며 다른 사용자의 키를 보내면 `400`.
- 같은 사용자의 `confirm`이 동시에 들어오면 두 번째는 `409`("처리 중"). 자동 결제도 같은 사용자 락을 씁니다.
- 결제 기록(`payment_records`)은 토스를 부르기 **전에** `PENDING`으로 만들고, 결과에 따라 `DONE`/`FAILED`로 바뀝니다. `orderId`는 `sub-<구독 ID 앞 12자>-<기간 시작 yyyyMMddHHmm>`로 결정되어 같은 기간을 다시 시도해도 같은 값이 나오며(토스가 중복 주문을 거절), 64자를 넘지 않습니다.
- 첫 결제 실패는 `402`와 실패 사유. 이때 구독은 `PAST_DUE`로 **저장된 채 남습니다**(카드 재등록으로 복구). 결제 키 미설정은 `503`.
- 토스 응답을 받지 못한 경우(네트워크 오류·중단)는 `502`이며 기록은 `PENDING`으로 남습니다. 자동으로 다시 청구하지 않고, 갱신 스케줄러는 1시간이 지나지 않은 `PENDING` 기록이 있는 구독을 건너뜁니다.
- 구독 상태: `ACTIVE`(정상) / `CANCELED`(해지 예약, 기간 종료 시 무료 전환) / `PAST_DUE`(첫 결제 실패 또는 자동결제 3회 연속 실패, 카드 재등록 필요). 사용자당 구독은 하나입니다(`uk_subscriptions_user`).
- 자동 결제는 `SubscriptionRenewalScheduler`가 1시간마다 `next_billing_at`이 지난 구독을 갱신합니다. 실패 시 하루 뒤 재시도, 3회 실패면 `PAST_DUE`. 새 기간은 기간 종료 후 3일(유예) 안이면 종료 시점부터, 그보다 늦으면 지금부터 시작합니다.
- 결제 성공 시 `user_plans`의 만료일을 기간 종료 + 3일(유예)로 **늘리기만** 합니다. 관리자가 더 늦게(또는 무기한) 부여한 요금제는 줄이지 않습니다. 해지는 `granted_by = subscription`인 행만 기간 종료일로 당깁니다.
- 토스 API 호출은 DB 트랜잭션 밖에서 실행되고, DB 변경은 단계별 짧은 트랜잭션(`SubscriptionStore`)으로 나뉩니다.
- 프론트 성공/실패 페이지: `/billing/success`, `/billing/fail`.

### 13.2 카카오톡 마감 알림 (나에게 보내기)

카카오 디벨로퍼스 앱에 카카오 로그인 활성화, Redirect URI `http://localhost:3000/kakao/callback`(운영은 실제 도메인), 동의항목 `talk_message`(카카오톡 메시지 전송)를 설정합니다. 친구에게 보내기는 별도 권한 심사가 필요해 사용하지 않습니다.

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/me/kakao` | `{ linked, configured, deadlineReminders, needsReconnect, linkedAt, lastNotifiedAt }` |
| `GET` | `/api/me/kakao/connect-url?redirectUri=` | 카카오 동의 화면 URL과 `state` |
| `POST` | `/api/me/kakao/link` | `{ code, redirectUri, state }` → 토큰 교환·저장 |
| `PUT` | `/api/me/kakao/preferences` | `{ deadlineReminders }` |
| `POST` | `/api/me/kakao/test` | 본인에게 테스트 메시지 |
| `DELETE` | `/api/me/kakao` | 연결 해제(카카오 unlink 포함) |

- `state`는 `connect-url`을 부를 때마다 새로 만드는 난수이며 서버가 사용자별로 10분 동안 기억합니다. `POST /link`에는 `state`가 **필수**이고, 없거나·만료됐거나·다르거나·이미 쓴 값이면 `400`입니다. 한 번 쓰면 소비되므로 콜백을 다시 보내려면 `connect-url`부터 다시 시작해야 합니다.
- 토큰은 `APP_TOKEN_ENCRYPTION_KEY`로 AES-GCM 암호화해 저장합니다. `KAKAO_REST_API_KEY`가 있는데 이 키가 없으면 서버가 기동하지 않습니다. 액세스 토큰(약 12시간)은 만료 5분 전에 리프레시 토큰(약 60일)으로 자동 갱신하고, 리프레시 토큰이 만료되면 `needsReconnect: true`.
- 갱신 때 카카오가 `invalid_grant`(KOE319)를 돌려주면 저장된 리프레시 토큰을 지워 `needsReconnect: true`로 만들고 `409`("다시 연결해 주세요")를 돌려줍니다.
- 마감 알림 발송은 사용자마다 따로 커밋되므로 한 사용자의 발송 실패가 다른 사용자의 발송 기록을 되돌리지 않습니다.
- `DeadlineReminderScheduler`가 매일 09:00(Asia/Seoul)에 알림을 켠 사용자마다 오늘·내일 마감인 본인 담당 미완료 업무를 한 번에 보냅니다. 같은 날 중복 발송은 `notification_logs`로 막습니다. 담당자 매칭은 프로젝트 `project_members.display_name`과 업무 `assignee_name`(쉼표 구분, "전체" 포함)입니다.

### 13.3 환경 변수

| 변수 | 설명 |
| --- | --- |
| `TOSS_CLIENT_KEY`, `TOSS_SECRET_KEY` | 없으면 문서용 '결제창/API 개별연동' 테스트 키(`test_ck_D5Ge…`, `test_sk_zXLk…`)를 사용. 결제위젯용 `test_gck_/test_gsk_` 키는 빌링 API에서 `NOT_FOUND_MERCHANT`가 남. 개발자센터 가입(이메일만) 후 자기 테스트 키로 바꾸면 개발자센터에서 결제 내역을 볼 수 있음 |
| `PREMIUM_PRICE_KRW` | 월 요금. 기본 4900 |
| `FRONTEND_BASE_URL` | 카카오 메시지 링크에 쓰는 프론트 주소. 기본 `http://localhost:3000` |
| `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET` | 카카오 앱 REST API 키(필수), 클라이언트 시크릿(앱에서 켰을 때만) |
| `DEADLINE_REMINDER_CRON` | 기본 `0 0 9 * * *` |
| `APP_TOKEN_ENCRYPTION_KEY` | 긴 임의 문자열. `KAKAO_REST_API_KEY`를 쓰면 필수(없으면 기동 거부) |
| `ADMIN_TEST_ENABLED` | 개발용 관리자 테스트 인증 활성화. 기본 `false` (11.3) |
| `CORS_ALLOWED_ORIGINS` | 허용 Origin 목록(쉼표, 정확한 값). 기본 `http://localhost:3000` (9) |

## 14. 2026-10-01 결함 수정으로 바뀐 계약

### 14.1 회의록 생성 재시도 방지
`POST /api/projects/{projectId}/minutes`는 선택 헤더 `Idempotency-Key`(8~100자, 영문·숫자·`_`·`-`)를 받습니다. 같은 프로젝트에서 같은 키로 다시 요청하면 AI를 다시 호출하지 않고 이미 만들어진 회의록을 `200`으로 돌려줍니다. 형식이 틀리면 `400`. 프론트는 폼 내용이 바뀔 때만 새 키를 만듭니다.

### 14.2 업무 고정 ID
회의록 응답의 `todos[]`에 `id`가 추가됐습니다. `PUT /api/projects/{projectId}/minutes/{id}`로 편집할 때 기존 업무는 `id`를 그대로 보내고 새 업무는 `id`를 비웁니다. 통합 업무(`integrated_todos.source_todo_id`)와 담당자 진행률(`project_todos.source_todo_id`)이 이 ID로 연결되므로, 업무를 지우거나 순서를 바꿔도 완료 상태가 다른 업무로 옮겨가지 않습니다. 회의록에서 사라진 업무는 보드와 진행률에서도 삭제됩니다. 옛 회의록(ID 없음)은 처음 동기화될 때 위치 기준으로 ID를 부여합니다.

### 14.3 진행률 식별자
`todo_member_progress.user_id`는 이제 Supabase `sub`입니다(이전: 표시 이름). 동명이인이 한 사람으로 합쳐지거나 이름을 바꾼 사용자가 자기 업무를 못 보는 문제가 사라집니다. 대시보드 `userId`도 `sub`를 돌려줍니다. 관리자 테스트 인증의 `X-Current-User-Id` 경로만 옛 표시 이름 키를 유지합니다.

### 14.4 레거시 이름 매칭 제거
`project_members` 행이 없는 프로젝트에 대해 JWT 이름·이메일로 접근을 허용하던 규칙(11.9 전환기 호환)이 삭제됐습니다. 접근 권한은 오직 `project_members.user_id`로만 판정합니다. 새 Supabase 프로젝트에는 해당 데이터가 없으므로 영향이 없습니다.

### 14.5 기타
- `DELETE /api/projects/{id}/permanent`는 휴지통(`DELETED`) 상태일 때만 동작하며, 그 외에는 `404`.
- 회의록 삭제 시 통합 업무 행을 함께 지우고, 전사로 만든 회의록이면 전사의 `minutesId`를 비워 같은 녹음으로 다시 만들 수 있게 합니다.
- 회의록 편집 시 `evidence`(근거 인용)를 항목 텍스트 기준으로 다시 정렬합니다. 지워진 항목의 근거는 버리고 새 항목은 빈 근거를 갖습니다.
- 제목·주제·담당자·마감 문자열은 255자로 잘라 저장합니다. `MinutesRequest.title`은 255자, `rawText`는 200,000자 제한.
- 프로젝트 종료일·마감일 판정은 `Asia/Seoul` 기준입니다.
- `GET /api/projects/{id}/todos`는 더 이상 조회 시점에 회의록을 재동기화하지 않습니다(생성·수정·삭제 시에만).
- Claude 호출(회의록 생성)은 DB 트랜잭션 밖에서 실행되며, 저장만 짧은 트랜잭션으로 묶입니다.
