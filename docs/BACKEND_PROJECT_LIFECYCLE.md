# Backend Project Lifecycle Guide

이 문서는 프로젝트 종료, 삭제, 휴지통, 프론트 호환 응답을 백엔드에서 동일한 기준으로 사용하기 위한 공용 기준이다.
새 백엔드 기능을 만들 때 프로젝트 상태를 직접 비교하지 말고, 아래 공용 메서드를 우선 사용한다.

## 용어 기준

| 의미 | 백엔드 내부 기준 | 프론트 호환 응답 | 설명 |
| --- | --- | --- | --- |
| 진행 중 | `ProjectStatus.ACTIVE` | `ACTIVE` | 종료일이 없고 삭제되지 않은 프로젝트 |
| 종료 예정 | `ProjectStatus.END_SCHEDULED` | `DISPOSAL_SCHEDULED` | `endDate`가 오늘 이후인 프로젝트 |
| 자동 종료 | `ProjectStatus.ENDED` | `DISPOSED` | `endDate`가 지난 프로젝트 |
| 사용자 삭제 | `ProjectStatus.DELETED` | `DELETED` | 사용자가 삭제해 휴지통으로 이동한 프로젝트 |

현재 프론트 호환을 위해 `DISPOSAL_SCHEDULED`, `DISPOSED`, `disposalDeadline`, `disposedAt` 응답은 유지한다.
백엔드 내부 신규 코드에서는 `END_SCHEDULED`, `ENDED`, `DELETED`, `endDate`, `endedAt`, `deletedAt`을 기준으로 작성한다.

## ProjectStatus 공용 메서드

파일: `backend/src/main/java/com/teample/entity/ProjectStatus.java`

| 메서드 | 반환 | 사용 목적 |
| --- | --- | --- |
| `isDeleted()` | `boolean` | 휴지통 이동 상태인지 확인 |
| `isEnded()` | `boolean` | 자동 종료 상태인지 확인 |
| `isVisibleInActiveList()` | `boolean` | 일반 프로젝트 목록에 노출할 수 있는 상태인지 확인 |
| `blocksNewMinutes()` | `boolean` | 상태 자체가 새 회의록 생성을 막는지 확인 |
| `toClientStatus()` | `String` | 프론트 호환 `status` 값으로 변환 |

## Project 엔티티 공용 메서드

파일: `backend/src/main/java/com/teample/entity/Project.java`

| 메서드 | 반환 | 사용 목적 |
| --- | --- | --- |
| `getResolvedStatus()` | `ProjectStatus` | `status == null`인 레거시 데이터도 `ACTIVE`로 안전하게 처리 |
| `isDeleted()` | `boolean` | 삭제/휴지통 상태 확인 |
| `isVisibleInActiveList()` | `boolean` | 일반 목록, 대시보드 목록 노출 필터 |
| `hasEndDatePassed(LocalDate today)` | `boolean` | 종료일이 지났는지 확인 |
| `blocksNewMinutes(LocalDate today)` | `boolean` | 회의록 생성 가능 여부 판단 |
| `synchronizeLifecycle(LocalDate today, LocalDateTime now)` | `void` | 종료일 기준으로 `ACTIVE`, `END_SCHEDULED`, `ENDED` 자동 보정 |
| `markDeleted(LocalDateTime now)` | `void` | 사용자 삭제 처리. `DELETED`, `deletedAt` 기록 |
| `restore(LocalDate today, LocalDateTime now)` | `void` | 휴지통 복원. 종료일 기준 상태로 재계산 |

## ProjectResponse 공용 변환

파일: `backend/src/main/java/com/teample/dto/ProjectResponse.java`

| 메서드 | 사용 목적 |
| --- | --- |
| `ProjectResponse.from(Project project)` | 프로젝트 응답 필드를 한 곳에서 통일해서 생성 |

`ProjectResponse.from(project)`는 다음을 함께 처리한다.

| 응답 필드 | 값 기준 | 비고 |
| --- | --- | --- |
| `endDate` | `project.endDate` | 백엔드 신규 표준 필드 |
| `disposalDeadline` | `project.endDate` | 프론트 호환 필드 |
| `status` | `project.getResolvedStatus().toClientStatus()` | 프론트 호환 상태값 |
| `endedAt` | `project.endedAt` | 백엔드 신규 표준 필드 |
| `disposedAt` | `project.endedAt` | 프론트 호환 필드 |
| `deletedAt` | `project.deletedAt` | 사용자 삭제 시각 |

## 권장 사용 예시

```java
// 일반 목록 필터
projects.stream()
        .filter(Project::isVisibleInActiveList)
        .toList();

// 회의록 생성 차단
if (project.blocksNewMinutes(LocalDate.now())) {
    throw new IllegalStateException("Ended or deleted projects cannot create minutes.");
}

// 조회 또는 스케줄러에서 생명주기 동기화
project.synchronizeLifecycle(LocalDate.now(), LocalDateTime.now());

// 삭제와 복원
project.markDeleted(LocalDateTime.now());
project.restore(LocalDate.now(), LocalDateTime.now());

// API 응답 변환
return ProjectResponse.from(project);
```

## 서비스별 사용 기준

| 기능 | 사용할 공용 기준 |
| --- | --- |
| 프로젝트 일반 목록 | `Project::isVisibleInActiveList` |
| 휴지통 목록 | `ProjectStatus.DELETED` repository 조회 또는 `Project::isDeleted` |
| 프로젝트 상세 조회 | 조회 후 `project.synchronizeLifecycle(today, now)` |
| 자동 종료 스케줄러 | `project.synchronizeLifecycle(today, now)` |
| 사용자 삭제 | `project.markDeleted(now)` |
| 휴지통 복원 | `project.restore(today, now)` |
| 영구 삭제 | 관련 todo/minutes/progress 삭제 후 `projectRepository.delete(project)` |
| 회의록 생성 가능 여부 | `project.blocksNewMinutes(today)` |
| 프로젝트 API 응답 | `ProjectResponse.from(project)` |

## 현재 호환 정책

기능 개발 중 다른 브랜치와 프론트 작업을 막지 않기 위해 기존 이름은 바로 제거하지 않는다.

- 요청 호환: `ProjectRequest.endDate`는 `disposalDeadline`도 받을 수 있다.
- 응답 호환: `disposalDeadline`, `disposedAt`, `DISPOSAL_SCHEDULED`, `DISPOSED`는 유지한다.
- 신규 백엔드 내부 코드: `endDate`, `endedAt`, `deletedAt`, `END_SCHEDULED`, `ENDED`, `DELETED`를 기준으로 작성한다.
- 기존 필드 제거는 프론트와 모든 백엔드 기능 전환이 끝난 뒤 한 번에 진행한다.

## 주의 사항

1. `DELETED`는 사용자가 삭제한 휴지통 상태다. 자동 종료와 섞지 않는다.
2. `ENDED`는 종료일이 지나 자동 종료된 상태다. 삭제된 상태가 아니다.
3. 새 회의록 생성은 `ENDED`, `DELETED`, 종료일 경과 상태에서 막는다.
4. 일반 목록과 대시보드 목록에서는 `DELETED`를 제외한다.
5. 기존 호환 필드와 상태값은 다른 개발 중인 프론트/백엔드가 사용할 수 있으므로 임의로 삭제하지 않는다.
