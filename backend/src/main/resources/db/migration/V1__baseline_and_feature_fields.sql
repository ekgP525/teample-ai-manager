CREATE TABLE IF NOT EXISTS projects (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    members JSON,
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS minutes (
    id VARCHAR(255) PRIMARY KEY,
    project_id VARCHAR(255) NOT NULL,
    meeting_date DATE NOT NULL,
    raw_text TEXT NOT NULL,
    title VARCHAR(255),
    topic VARCHAR(255),
    discussions JSON,
    decisions JSON,
    pending JSON,
    todos JSON,
    next_agenda JSON,
    created_at TIMESTAMP,
    CONSTRAINT fk_minutes_project FOREIGN KEY (project_id) REFERENCES projects(id)
);

ALTER TABLE projects ADD COLUMN IF NOT EXISTS disposal_deadline DATE;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS status VARCHAR(32) DEFAULT 'ACTIVE';
ALTER TABLE projects ADD COLUMN IF NOT EXISTS disposed_at TIMESTAMP;
ALTER TABLE minutes ADD COLUMN IF NOT EXISTS evidence JSON;

UPDATE projects SET status = 'ACTIVE' WHERE status IS NULL;
ALTER TABLE projects ALTER COLUMN status SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_minutes_project_created_at
    ON minutes (project_id, created_at);
CREATE INDEX IF NOT EXISTS idx_projects_status_disposal_deadline
    ON projects (status, disposal_deadline);
