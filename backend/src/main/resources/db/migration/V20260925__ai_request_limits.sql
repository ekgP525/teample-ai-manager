CREATE TABLE ai_admission_lock (id INTEGER PRIMARY KEY);
INSERT INTO ai_admission_lock (id) VALUES (1);
CREATE TABLE ai_requests (
    user_id VARCHAR(255) NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    minutes_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, request_key)
);
CREATE INDEX idx_ai_requests_user_created ON ai_requests(user_id, created_at);
CREATE INDEX idx_ai_requests_status_created ON ai_requests(status, created_at);

ALTER TABLE minutes ADD COLUMN next_todo_index INTEGER NOT NULL DEFAULT 0;
