CREATE TABLE environment_case (
    source_id UUID PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    payload JSONB NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    correlation_id UUID NOT NULL,
    order_id BIGINT UNIQUE REFERENCES orden_trabajo(id),
    actionable BOOLEAN NOT NULL
);
ALTER TABLE orden_trabajo DROP CONSTRAINT ck_orden_origin;
ALTER TABLE orden_trabajo ADD CONSTRAINT ck_orden_origin CHECK (origin IN ('MANUAL','PROYECTO','ATENCION_CIUDADANA','INSPECCION','AMBIENTE'));
