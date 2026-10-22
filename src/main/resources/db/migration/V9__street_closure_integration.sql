ALTER TABLE street_closure ADD COLUMN source_ref UUID;
ALTER TABLE street_closure ADD COLUMN correlation_id UUID;
ALTER TABLE street_closure ADD COLUMN request_event_id UUID;
ALTER TABLE street_closure ADD COLUMN requested_from_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE street_closure ADD COLUMN requested_to_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE street_closure ADD COLUMN requested_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE street_closure ADD COLUMN previous_closure_request_id VARCHAR(255) REFERENCES street_closure(closure_request_id);
ALTER TABLE street_closure ADD COLUMN integration_status VARCHAR(20) CHECK (integration_status IN ('PENDING','APPROVED','REJECTED','ACTIVE','EXPIRED','ENDED'));
ALTER TABLE street_closure ADD COLUMN integration_request JSONB;
ALTER TABLE street_closure ADD COLUMN authorization_data JSONB;
ALTER TABLE street_closure ADD COLUMN completion JSONB;
ALTER TABLE street_closure ADD COLUMN street_closure_id UUID;
ALTER TABLE street_closure ADD COLUMN authorized_start TIMESTAMP WITH TIME ZONE;
ALTER TABLE street_closure ADD COLUMN authorized_end TIMESTAMP WITH TIME ZONE;
ALTER TABLE street_closure ADD COLUMN last_event_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX idx_street_closure_integration_order ON street_closure(work_order_id,integration_status);
CREATE TABLE road_accident_projection (
    accident_reference VARCHAR(180) PRIMARY KEY,
    payload JSONB NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    correlation_id UUID NOT NULL
);
