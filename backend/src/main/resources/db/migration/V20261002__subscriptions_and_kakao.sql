create table if not exists subscriptions (
    id varchar(255) primary key,
    user_id varchar(255) not null,
    customer_key varchar(64) not null,
    billing_key varchar(255) not null,
    status varchar(32) not null,
    plan varchar(32) not null,
    amount integer not null,
    card_company varchar(64),
    card_number varchar(64),
    current_period_start timestamp not null,
    current_period_end timestamp not null,
    next_billing_at timestamp,
    failed_attempts integer not null default 0,
    last_error varchar(1000),
    canceled_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_subscriptions_user_id
    on subscriptions (user_id);

create index if not exists idx_subscriptions_status_next_billing
    on subscriptions (status, next_billing_at);

create table if not exists payment_records (
    id varchar(255) primary key,
    subscription_id varchar(255),
    user_id varchar(255) not null,
    order_id varchar(64) not null,
    payment_key varchar(255),
    order_name varchar(255),
    amount integer not null,
    status varchar(32) not null,
    failure_message varchar(1000),
    approved_at timestamp,
    created_at timestamp not null,
    constraint uk_payment_records_order_id unique (order_id)
);

create index if not exists idx_payment_records_user_created
    on payment_records (user_id, created_at);

create table if not exists kakao_links (
    user_id varchar(255) primary key,
    kakao_user_id varchar(64),
    access_token varchar(2000) not null,
    refresh_token varchar(2000),
    access_expires_at timestamp,
    refresh_expires_at timestamp,
    scopes varchar(255),
    deadline_reminders boolean not null default true,
    last_notified_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists notification_logs (
    id varchar(255) primary key,
    user_id varchar(255) not null,
    channel varchar(32) not null,
    dedupe_key varchar(255) not null,
    sent_at timestamp not null,
    constraint uk_notification_logs_dedupe unique (user_id, channel, dedupe_key)
);
