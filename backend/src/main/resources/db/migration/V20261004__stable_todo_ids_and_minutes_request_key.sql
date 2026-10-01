-- 업무 정체성을 배열 인덱스 대신 회의록 JSON 안의 고정 ID(source_todo_id)로 바꾼다.
alter table integrated_todos add column if not exists source_todo_id varchar(64);
alter table project_todos add column if not exists source_todo_id varchar(64);

alter table integrated_todos drop constraint if exists uk_integrated_todos_minutes_source;
alter table project_todos drop constraint if exists uk_project_todo_source;

create unique index if not exists uk_integrated_todos_minutes_source_todo
    on integrated_todos (minutes_id, source_todo_id);
create unique index if not exists uk_project_todos_minutes_source_todo
    on project_todos (minutes_id, source_todo_id);

-- 회의록 생성 재시도 중복 방지용 Idempotency-Key
alter table minutes add column if not exists request_key varchar(100);
create unique index if not exists uk_minutes_project_request_key
    on minutes (project_id, request_key);
