ALTER TABLE dispatch_allocations
    ADD COLUMN source_available_power_kw NUMERIC(14, 3);

UPDATE dispatch_allocations
SET source_available_power_kw = allocated_power_kw;

ALTER TABLE dispatch_allocations
    ALTER COLUMN source_available_power_kw SET NOT NULL,
    ADD CONSTRAINT dispatch_allocations_source_power_check CHECK (
        source_available_power_kw > 0
        AND source_available_power_kw >= allocated_power_kw
    );

CREATE TABLE device_commands (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    dispatch_id UUID NOT NULL,
    site_id UUID NOT NULL,
    device_id UUID NOT NULL,
    command_type VARCHAR(32) NOT NULL,
    target_power_kw NUMERIC(14, 3) NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    applied_power_kw NUMERIC(14, 3),
    rejection_reason VARCHAR(500),
    requested_at TIMESTAMPTZ NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT device_commands_org_id_key UNIQUE (organization_id, id),
    CONSTRAINT device_commands_dispatch_device_key UNIQUE (dispatch_id, device_id),
    CONSTRAINT device_commands_dispatch_fk
        FOREIGN KEY (organization_id, dispatch_id)
        REFERENCES dispatches (organization_id, id),
    CONSTRAINT device_commands_type_check CHECK (command_type = 'SET_POWER'),
    CONSTRAINT device_commands_status_check CHECK (
        status IN ('REQUESTED', 'ACCEPTED', 'REJECTED')
    ),
    CONSTRAINT device_commands_interval_check CHECK (valid_from < expires_at),
    CONSTRAINT device_commands_ack_check CHECK (
        (status = 'REQUESTED'
            AND applied_power_kw IS NULL
            AND rejection_reason IS NULL
            AND acknowledged_at IS NULL)
        OR
        (status = 'ACCEPTED'
            AND applied_power_kw IS NOT NULL
            AND rejection_reason IS NULL
            AND acknowledged_at IS NOT NULL)
        OR
        (status = 'REJECTED'
            AND applied_power_kw IS NOT NULL
            AND rejection_reason IS NOT NULL
            AND acknowledged_at IS NOT NULL)
    ),
    CONSTRAINT device_commands_version_check CHECK (version >= 0)
);

CREATE INDEX device_commands_dispatch_status_idx
    ON device_commands (organization_id, dispatch_id, status);

CREATE TABLE event_outbox (
    event_id UUID PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    partition_key VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error VARCHAR(1000),
    CONSTRAINT event_outbox_topic_check CHECK (length(btrim(topic)) > 0),
    CONSTRAINT event_outbox_key_check CHECK (length(btrim(partition_key)) > 0),
    CONSTRAINT event_outbox_payload_check CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT event_outbox_attempts_check CHECK (attempts >= 0)
);

CREATE INDEX event_outbox_ready_idx
    ON event_outbox (next_attempt_at, occurred_at)
    WHERE published_at IS NULL;

CREATE TABLE event_inbox (
    consumer_name VARCHAR(120) NOT NULL,
    event_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (consumer_name, event_id),
    CONSTRAINT event_inbox_consumer_check CHECK (length(btrim(consumer_name)) > 0),
    CONSTRAINT event_inbox_type_check CHECK (length(btrim(event_type)) > 0)
);

CREATE INDEX event_inbox_received_at_idx ON event_inbox (received_at);
