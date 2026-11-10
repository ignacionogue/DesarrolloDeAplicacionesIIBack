CREATE TABLE ticket_projection (
    ticket_id UUID PRIMARY KEY,
    snapshot JSONB NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    routing JSONB,
    routing_at TIMESTAMP WITH TIME ZONE,
    information JSONB,
    information_at TIMESTAMP WITH TIME ZONE,
    escalation JSONB,
    escalation_at TIMESTAMP WITH TIME ZONE,
    correlation_id UUID NOT NULL,
    order_id BIGINT UNIQUE REFERENCES orden_trabajo(id),
    started BOOLEAN NOT NULL DEFAULT FALSE,
    planning_review_required BOOLEAN NOT NULL DEFAULT FALSE,
    evaluation_required BOOLEAN NOT NULL DEFAULT FALSE
);
