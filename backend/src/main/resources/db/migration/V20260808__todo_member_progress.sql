create table if not exists project_todos (
    id varchar(255) primary key,
    project_id varchar(255) not null,
    minutes_id varchar(255) not null,
    source_index integer not null,
    source_assignee varchar(255),
    task text not null,
    deadline varchar(255),
    created_at timestamp,
    updated_at timestamp,
    constraint uk_project_todo_source unique (project_id, minutes_id, source_index),
    constraint fk_project_todos_project foreign key (project_id) references projects(id) on delete cascade,
    constraint fk_project_todos_minutes foreign key (minutes_id) references minutes(id) on delete cascade
);

create table if not exists todo_member_progress (
    id varchar(255) primary key,
    todo_id varchar(255) not null,
    project_id varchar(255) not null,
    minutes_id varchar(255) not null,
    user_id varchar(255) not null,
    member_name varchar(255) not null,
    assigned boolean not null default true,
    completed boolean not null default false,
    status varchar(32) not null default 'TODO',
    completed_at timestamp,
    created_at timestamp,
    updated_at timestamp,
    constraint uk_todo_member_progress unique (todo_id, user_id),
    constraint fk_todo_member_progress_todo foreign key (todo_id) references project_todos(id) on delete cascade,
    constraint fk_todo_member_progress_project foreign key (project_id) references projects(id) on delete cascade,
    constraint fk_todo_member_progress_minutes foreign key (minutes_id) references minutes(id) on delete cascade
);

create index if not exists idx_project_todos_project_id on project_todos(project_id);
create index if not exists idx_project_todos_minutes_id on project_todos(minutes_id);
create index if not exists idx_todo_member_progress_project_user on todo_member_progress(project_id, user_id);
create index if not exists idx_todo_member_progress_todo_user on todo_member_progress(todo_id, user_id);
create index if not exists idx_todo_member_progress_user on todo_member_progress(user_id);