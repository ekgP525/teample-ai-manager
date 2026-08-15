alter table projects add column if not exists end_date date;
alter table projects add column if not exists ended_at timestamp;
alter table projects add column if not exists deleted_at timestamp;

update projects
set end_date = disposal_deadline
where end_date is null
  and disposal_deadline is not null;

update projects
set ended_at = disposed_at
where ended_at is null
  and disposed_at is not null;

update projects
set status = 'END_SCHEDULED'
where status = 'DISPOSAL_SCHEDULED';

update projects
set status = 'ENDED'
where status = 'DISPOSED';

create index if not exists idx_projects_status_end_date
    on projects (status, end_date);
