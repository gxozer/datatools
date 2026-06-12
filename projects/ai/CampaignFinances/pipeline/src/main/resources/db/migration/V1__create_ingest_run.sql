CREATE TABLE ingest_run (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    source      VARCHAR(32) NOT NULL,
    started_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP   NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    row_counts  JSON        NULL
);
