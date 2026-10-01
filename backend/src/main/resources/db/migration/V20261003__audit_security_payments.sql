-- 한 사용자는 구독 하나만 가진다 (H2/Postgres 공통으로 unique index 사용).
create unique index if not exists uk_subscriptions_user
    on subscriptions (user_id);

-- 결제 시도마다 어느 기간을 결제하는지 남긴다. orderId가 기간 단위로 결정되므로 재시도 추적에 쓴다.
alter table payment_records add column if not exists period_start timestamp;

create index if not exists idx_payment_records_subscription_status
    on payment_records (subscription_id, status);
