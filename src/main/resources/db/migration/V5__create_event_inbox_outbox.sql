CREATE TABLE event_inbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    event_version VARCHAR(20) NOT NULL,
    source_module VARCHAR(60) NOT NULL,
    correlation_id UUID,
    causation_id UUID,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payload JSONB NOT NULL,
    envelope JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    error VARCHAR(60),
    processed_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (status IN ('RECEIVED','PROCESSED','REJECTED','ERROR'))
);
CREATE INDEX idx_inbox_pending ON event_inbox(status,next_attempt_at);

CREATE TABLE event_outbox (
    id UUID PRIMARY KEY,
    sequence_number BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    event_id UUID NOT NULL UNIQUE,
    ordering_key VARCHAR(180) NOT NULL,
    envelope JSONB NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(60),
    CHECK (status IN ('PENDING','PUBLISHED'))
);
CREATE INDEX idx_outbox_pending ON event_outbox(status,next_attempt_at);
CREATE INDEX idx_outbox_ordering ON event_outbox(ordering_key,sequence_number,status);
