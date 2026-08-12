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
    CONSTRAINT event_outbox_partition_key_check CHECK (length(btrim(partition_key)) > 0),
    CONSTRAINT event_outbox_payload_check CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT event_outbox_attempts_check CHECK (attempts >= 0)
);

CREATE INDEX event_outbox_ready_idx
    ON event_outbox (next_attempt_at, occurred_at)
    WHERE published_at IS NULL;
