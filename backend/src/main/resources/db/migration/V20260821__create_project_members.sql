create table if not exists project_members (
    id varchar(255) primary key,
    project_id varchar(255) not null,
    user_id varchar(255) not null,
    display_name varchar(255) not null,
    role varchar(32) not null,
    joined_at timestamp not null,
    constraint fk_project_members_project foreign key (project_id) references projects(id),
    constraint uk_project_members_project_user unique (project_id, user_id)
);

create index if not exists idx_project_members_project_id
    on project_members (project_id);

create index if not exists idx_project_members_user_id
    on project_members (user_id);