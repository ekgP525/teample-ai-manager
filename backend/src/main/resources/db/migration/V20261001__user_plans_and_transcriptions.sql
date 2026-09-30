create table if not exists user_plans (
    user_id varchar(255) primary key,
    plan varchar(32) not null,
    granted_by varchar(255),
    note varchar(500),
    expires_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists transcriptions (
    id varchar(255) primary key,
    project_id varchar(255) not null,
    created_by varchar(255) not null,
    status varchar(32) not null,
    source_file_name varchar(500),
    content_type varchar(255),
    file_size bigint,
    media_kind varchar(32),
    storage_path varchar(1000),
    duration_ms bigint,
    provider varchar(64),
    provider_job_id varchar(255),
    language varchar(16),
    expected_speakers integer,
    segments text,
    speaker_names text,
    error_message varchar(2000),
    minutes_id varchar(255),
    created_at timestamp not null,
    updated_at timestamp not null,
    completed_at timestamp,
    constraint fk_transcriptions_project foreign key (project_id) references projects(id)
);

create index if not exists idx_transcriptions_project_id
    on transcriptions (project_id);

create index if not exists idx_transcriptions_created_by_created_at
    on transcriptions (created_by, created_at);

create index if not exists idx_transcriptions_status
    on transcriptions (status);

alter table minutes add column if not exists transcription_id varchar(255);
