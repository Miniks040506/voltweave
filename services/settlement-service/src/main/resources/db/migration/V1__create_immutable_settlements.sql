CREATE TABLE settlements (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    dispatch_id UUID NOT NULL,
    vpp_id UUID NOT NULL,
    completion_status VARCHAR(32) NOT NULL,
    target_power_kw NUMERIC(14, 3) NOT NULL,
    scheduled_start_at TIMESTAMPTZ NOT NULL,
    scheduled_end_at TIMESTAMPTZ NOT NULL,
    baseline_frozen_at TIMESTAMPTZ NOT NULL,
    baseline_id UUID NOT NULL,
    baseline_version BIGINT NOT NULL,
    baseline_model_name VARCHAR(100) NOT NULL,
    baseline_model_version VARCHAR(50) NOT NULL,
    expected_energy_kwh NUMERIC(18, 6) NOT NULL,
    delivered_energy_kwh NUMERIC(18, 6) NOT NULL,
    achievement_percent NUMERIC(9, 3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT settlements_org_id_key UNIQUE (organization_id, id),
    CONSTRAINT settlements_dispatch_key UNIQUE (organization_id, dispatch_id),
    CONSTRAINT settlements_completion_status_check CHECK (
        completion_status IN ('COMPLETED', 'PARTIALLY_COMPLETED')
    ),
    CONSTRAINT settlements_status_check CHECK (status = 'CALCULATED'),
    CONSTRAINT settlements_values_check CHECK (
        target_power_kw > 0
        AND scheduled_start_at < scheduled_end_at
        AND baseline_version > 0
        AND expected_energy_kwh > 0
        AND delivered_energy_kwh >= 0
        AND achievement_percent >= 0
    )
);

CREATE INDEX settlements_dispatch_idx
    ON settlements (organization_id, dispatch_id);

CREATE TABLE settlement_baseline_points (
    organization_id UUID NOT NULL,
    settlement_id UUID NOT NULL,
    forecast_at TIMESTAMPTZ NOT NULL,
    baseline_grid_import_kw NUMERIC(14, 3) NOT NULL,
    PRIMARY KEY (settlement_id, forecast_at),
    CONSTRAINT settlement_baseline_points_settlement_fk
        FOREIGN KEY (organization_id, settlement_id)
        REFERENCES settlements (organization_id, id),
    CONSTRAINT settlement_baseline_points_value_check CHECK (
        baseline_grid_import_kw >= 0
    )
);

CREATE TABLE settlement_lines (
    organization_id UUID NOT NULL,
    settlement_id UUID NOT NULL,
    site_id UUID NOT NULL,
    participant_id UUID NOT NULL,
    participant_type VARCHAR(32) NOT NULL,
    requested_power_kw NUMERIC(14, 3) NOT NULL,
    expected_energy_kwh NUMERIC(18, 6) NOT NULL,
    delivered_energy_kwh NUMERIC(18, 6) NOT NULL,
    achievement_percent NUMERIC(9, 3) NOT NULL,
    PRIMARY KEY (settlement_id, participant_id),
    CONSTRAINT settlement_lines_settlement_fk
        FOREIGN KEY (organization_id, settlement_id)
        REFERENCES settlements (organization_id, id),
    CONSTRAINT settlement_lines_values_check CHECK (
        requested_power_kw > 0
        AND expected_energy_kwh > 0
        AND delivered_energy_kwh >= 0
        AND achievement_percent >= 0
    )
);

CREATE INDEX settlement_lines_site_idx
    ON settlement_lines (organization_id, site_id, settlement_id);

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

CREATE FUNCTION reject_settlement_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'settlement calculation is immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER settlements_immutable
    BEFORE UPDATE OR DELETE ON settlements
    FOR EACH ROW EXECUTE FUNCTION reject_settlement_mutation();

CREATE TRIGGER settlement_baseline_points_immutable
    BEFORE UPDATE OR DELETE ON settlement_baseline_points
    FOR EACH ROW EXECUTE FUNCTION reject_settlement_mutation();

CREATE TRIGGER settlement_lines_immutable
    BEFORE UPDATE OR DELETE ON settlement_lines
    FOR EACH ROW EXECUTE FUNCTION reject_settlement_mutation();
