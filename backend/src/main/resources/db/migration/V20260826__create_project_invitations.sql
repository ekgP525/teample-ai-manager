create table if not exists project_invitations (
    id varchar(255) primary key,
    project_id varchar(255) not null,
    code varchar(32) not null,
    created_by varchar(255) not null,
    expires_at timestamp not null,
    created_at timestamp not null,
    active boolean not null default true,
    constraint fk_project_invitations_project foreign key (project_id) references projects(id),
    constraint uk_project_invitations_code unique (code)
);

create index if not exists idx_project_invitations_project_id
    on project_invitations (project_id);

create index if not exists idx_project_invitations_code_active
    on project_invitations (code, active);