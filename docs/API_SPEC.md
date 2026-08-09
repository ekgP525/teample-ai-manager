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
