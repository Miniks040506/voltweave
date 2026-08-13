CREATE TABLE device_telemetry_projection (
    organization_id UUID NOT NULL,
    site_id UUID NOT NULL,
    device_id UUID PRIMARY KEY,
    device_type VARCHAR(32) NOT NULL,
    last_observed_at TIMESTAMPTZ NOT NULL,
    last_received_at TIMESTAMPTZ NOT NULL,
    active_power_kw NUMERIC(14, 3) NOT NULL,
    soc_percent NUMERIC(5, 2),
    online BOOLEAN NOT NULL,
    quality VARCHAR(16) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT device_telemetry_projection_type_check
        CHECK (device_type IN ('SMART_METER', 'BATTERY', 'EV_CHARGER')),
    CONSTRAINT device_telemetry_projection_quality_check
        CHECK (quality IN ('VALID', 'STALE', 'OUT_OF_ORDER')),
    CONSTRAINT device_telemetry_projection_soc_check
        CHECK (soc_percent IS NULL OR soc_percent BETWEEN 0 AND 100)
);

CREATE INDEX device_telemetry_projection_site_type_idx
    ON device_telemetry_projection (site_id, device_type, last_observed_at DESC);
