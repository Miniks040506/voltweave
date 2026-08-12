DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_extension WHERE extname = 'timescaledb'
    ) THEN
        RAISE EXCEPTION 'timescaledb extension must be installed in telemetry_db';
    END IF;
END
$$;

CREATE TABLE telemetry_points (
    organization_id UUID NOT NULL,
    site_id UUID NOT NULL,
    device_id UUID NOT NULL,
    sequence_number BIGINT NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    device_type VARCHAR(32) NOT NULL,
    active_power_kw NUMERIC(12, 3) NOT NULL,
    soc_percent NUMERIC(6, 3),
    online BOOLEAN NOT NULL,
    metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY (device_id, observed_at, sequence_number),
    CONSTRAINT telemetry_points_sequence_check CHECK (sequence_number > 0),
    CONSTRAINT telemetry_points_time_check CHECK (received_at >= observed_at),
    CONSTRAINT telemetry_points_type_check CHECK (
        device_type IN ('SMART_METER', 'SOLAR_INVERTER', 'BATTERY', 'EV_CHARGER')
    ),
    CONSTRAINT telemetry_points_soc_check CHECK (
        soc_percent IS NULL OR soc_percent BETWEEN 0 AND 100
    ),
    CONSTRAINT telemetry_points_metrics_check CHECK (jsonb_typeof(metrics) = 'object')
);

SELECT create_hypertable(
    'telemetry_points', by_range('observed_at'), if_not_exists => TRUE
);

CREATE INDEX telemetry_points_tenant_time_idx
    ON telemetry_points (organization_id, observed_at DESC);
CREATE INDEX telemetry_points_device_time_idx
    ON telemetry_points (organization_id, device_id, observed_at DESC);

CREATE TABLE telemetry_dedup (
    organization_id UUID NOT NULL,
    device_id UUID NOT NULL,
    sequence_number BIGINT NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (device_id, sequence_number),
    CONSTRAINT telemetry_dedup_sequence_check CHECK (sequence_number > 0),
    CONSTRAINT telemetry_dedup_expiry_check CHECK (expires_at > observed_at)
);

CREATE INDEX telemetry_dedup_expiry_idx ON telemetry_dedup (expires_at);

CREATE TABLE quarantined_telemetry (
    id UUID PRIMARY KEY,
    organization_id UUID,
    device_id UUID,
    reason_code VARCHAR(64) NOT NULL,
    reason_detail VARCHAR(500) NOT NULL,
    raw_payload JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT quarantined_telemetry_reason_check CHECK (
        length(btrim(reason_code)) > 0 AND length(btrim(reason_detail)) > 0
    )
);

CREATE INDEX quarantined_telemetry_received_idx
    ON quarantined_telemetry (received_at DESC);

CREATE TABLE device_twins (
    organization_id UUID NOT NULL,
    site_id UUID NOT NULL,
    device_id UUID PRIMARY KEY,
    device_type VARCHAR(32) NOT NULL,
    last_sequence_number BIGINT NOT NULL,
    last_observed_at TIMESTAMPTZ NOT NULL,
    active_power_kw NUMERIC(12, 3) NOT NULL,
    soc_percent NUMERIC(6, 3),
    online BOOLEAN NOT NULL,
    metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (organization_id, device_id),
    CONSTRAINT device_twins_sequence_check CHECK (last_sequence_number > 0),
    CONSTRAINT device_twins_type_check CHECK (
        device_type IN ('SMART_METER', 'SOLAR_INVERTER', 'BATTERY', 'EV_CHARGER')
    ),
    CONSTRAINT device_twins_soc_check CHECK (
        soc_percent IS NULL OR soc_percent BETWEEN 0 AND 100
    ),
    CONSTRAINT device_twins_metrics_check CHECK (jsonb_typeof(metrics) = 'object'),
    CONSTRAINT device_twins_time_check CHECK (updated_at >= last_observed_at)
);

CREATE INDEX device_twins_site_idx ON device_twins (organization_id, site_id);
