CREATE TABLE IF NOT EXISTS integrated_todos (
    id VARCHAR(255) PRIMARY KEY,
    project_id VARCHAR(255) NOT NULL,
    minutes_id VARCHAR(255),
    source_index INTEGER,
    content TEXT NOT NULL,
    assignee_id VARCHAR(255),
    assignee_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) DEFAULT 'TODO' NOT NULL,
    priority_order INTEGER NOT NULL,
    due_date DATE,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_integrated_todos_project
        FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_integrated_todos_minutes
        FOREIGN KEY (minutes_id) REFERENCES minutes(id) ON DELETE SET NULL,
    CONSTRAINT uk_integrated_todos_minutes_source
        UNIQUE (minutes_id, source_index)
);

CREATE INDEX IF NOT EXISTS idx_integrated_todos_project_status_priority
    ON integrated_todos (project_id, status, priority_order);
CREATE INDEX IF NOT EXISTS idx_integrated_todos_minutes
    ON integrated_todos (minutes_id);
