CREATE TABLE reward_ledger_entries (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    settlement_id UUID NOT NULL,
    participant_id UUID NOT NULL,
    entry_type VARCHAR(32) NOT NULL,
    energy_kwh NUMERIC(18, 6),
    rate_per_kwh NUMERIC(19, 6),
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    rounding_mode VARCHAR(16) NOT NULL,
    source_event_id UUID,
    idempotency_key VARCHAR(100),
    reason VARCHAR(500) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT reward_ledger_org_id_key UNIQUE (organization_id, id),
    CONSTRAINT reward_ledger_settlement_fk
        FOREIGN KEY (organization_id, settlement_id)
        REFERENCES settlements (organization_id, id),
    CONSTRAINT reward_ledger_entry_type_check CHECK (
        entry_type IN ('BASE_REWARD', 'ADJUSTMENT', 'REVERSAL')
    ),
    CONSTRAINT reward_ledger_currency_check CHECK (
        currency = upper(currency) AND length(currency) = 3
    ),
    CONSTRAINT reward_ledger_rounding_check CHECK (rounding_mode = 'HALF_UP'),
    CONSTRAINT reward_ledger_source_check CHECK (
        (entry_type = 'BASE_REWARD'
            AND energy_kwh >= 0 AND rate_per_kwh >= 0 AND amount >= 0
            AND source_event_id IS NOT NULL AND idempotency_key IS NULL)
        OR
        (entry_type IN ('ADJUSTMENT', 'REVERSAL')
            AND energy_kwh IS NULL AND rate_per_kwh IS NULL AND amount <> 0
            AND source_event_id IS NULL AND idempotency_key IS NOT NULL)
    )
);

CREATE UNIQUE INDEX reward_ledger_base_entry_key
    ON reward_ledger_entries (organization_id, settlement_id, participant_id)
    WHERE entry_type = 'BASE_REWARD';

CREATE UNIQUE INDEX reward_ledger_source_event_key
    ON reward_ledger_entries (source_event_id, participant_id)
    WHERE source_event_id IS NOT NULL;

CREATE UNIQUE INDEX reward_ledger_idempotency_key
    ON reward_ledger_entries (organization_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX reward_ledger_participant_time_idx
    ON reward_ledger_entries (organization_id, participant_id, created_at DESC);

CREATE TABLE api_idempotency (
    organization_id UUID NOT NULL,
    operation VARCHAR(120) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    resource_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (organization_id, operation, idempotency_key),
    CONSTRAINT api_idempotency_resource_fk
        FOREIGN KEY (organization_id, resource_id)
        REFERENCES reward_ledger_entries (organization_id, id)
);

CREATE TRIGGER reward_ledger_entries_immutable
    BEFORE UPDATE OR DELETE ON reward_ledger_entries
    FOR EACH ROW EXECUTE FUNCTION reject_settlement_mutation();
