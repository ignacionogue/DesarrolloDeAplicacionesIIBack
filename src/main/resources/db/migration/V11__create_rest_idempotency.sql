CREATE TABLE rest_idempotency (
    request_scope VARCHAR(500) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    response_status INTEGER,
    response_content_type VARCHAR(120),
    response_etag VARCHAR(80),
    response_body TEXT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
