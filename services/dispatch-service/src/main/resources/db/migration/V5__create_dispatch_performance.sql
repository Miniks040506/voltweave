CREATE TABLE dispatch_performance_points (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    dispatch_id UUID NOT NULL,
    site_id UUID NOT NULL,
    device_id UUID NOT NULL,
    telemetry_event_id UUID NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    target_power_kw NUMERIC(14, 3) NOT NULL,
    requested_power_kw NUMERIC(14, 3) NOT NULL,
    actual_power_kw NUMERIC(14, 3) NOT NULL,
    delivered_power_kw NUMERIC(14, 3) NOT NULL,
    error_kw NUMERIC(14, 3) NOT NULL,
    error_percent NUMERIC(18, 3) NOT NULL,
    cumulative_delivered_energy_kwh NUMERIC(18, 6) NOT NULL,
    online BOOLEAN NOT NULL,
    quality VARCHAR(32) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT dispatch_performance_dispatch_fk
        FOREIGN KEY (organization_id, dispatch_id)
        REFERENCES dispatches (organization_id, id),
    CONSTRAINT dispatch_performance_allocation_fk
        FOREIGN KEY (dispatch_id, device_id)
        REFERENCES dispatch_allocations (dispatch_id, device_id),
    CONSTRAINT dispatch_performance_event_key
        UNIQUE (dispatch_id, telemetry_event_id),
    CONSTRAINT dispatch_performance_values_check CHECK (
        requested_power_kw > 0
        AND delivered_power_kw >= 0
        AND cumulative_delivered_energy_kwh >= 0
    ),
    CONSTRAINT dispatch_performance_quality_check CHECK (quality = 'VALID')
);

CREATE INDEX dispatch_performance_timeline_idx
    ON dispatch_performance_points (organization_id, dispatch_id, observed_at, device_id);
