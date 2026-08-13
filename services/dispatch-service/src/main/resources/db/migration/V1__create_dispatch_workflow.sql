CREATE TABLE dispatches (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    vpp_id UUID NOT NULL,
    optimization_preview_id UUID NOT NULL,
    optimization_preview_version BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    target_power_kw NUMERIC(14, 3) NOT NULL,
    required_power_kw NUMERIC(14, 3) NOT NULL,
    planned_power_kw NUMERIC(14, 3) NOT NULL,
    scheduled_start_at TIMESTAMPTZ NOT NULL,
    scheduled_end_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT dispatches_org_id_key UNIQUE (organization_id, id),
    CONSTRAINT dispatches_type_check CHECK (type = 'REDUCE_DEMAND'),
    CONSTRAINT dispatches_status_check CHECK (status IN (
        'DRAFT', 'SCHEDULED', 'PREPARING', 'ACTIVE', 'REBALANCING',
        'COMPLETING', 'COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED', 'CANCELLED'
    )),
    CONSTRAINT dispatches_values_check CHECK (
        optimization_preview_version > 0
        AND target_power_kw > 0
        AND required_power_kw >= target_power_kw
        AND planned_power_kw >= required_power_kw
        AND scheduled_start_at < scheduled_end_at
        AND version >= 0
    )
);

CREATE INDEX dispatches_vpp_schedule_idx
    ON dispatches (organization_id, vpp_id, scheduled_start_at DESC);

CREATE TABLE dispatch_allocations (
    organization_id UUID NOT NULL,
    dispatch_id UUID NOT NULL,
    site_id UUID NOT NULL,
    device_id UUID NOT NULL,
    device_type VARCHAR(32) NOT NULL,
    allocated_power_kw NUMERIC(14, 3) NOT NULL,
    expected_energy_kwh NUMERIC(14, 3) NOT NULL,
    score NUMERIC(5, 4) NOT NULL,
    PRIMARY KEY (dispatch_id, device_id),
    CONSTRAINT dispatch_allocations_dispatch_fk
        FOREIGN KEY (organization_id, dispatch_id)
        REFERENCES dispatches (organization_id, id),
    CONSTRAINT dispatch_allocations_values_check CHECK (
        allocated_power_kw > 0 AND expected_energy_kwh > 0 AND score BETWEEN 0 AND 1
    )
);

CREATE TABLE dispatch_baselines (
    dispatch_id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    forecast_id UUID NOT NULL,
    forecast_version BIGINT NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    model_version VARCHAR(50) NOT NULL,
    source_valid_until TIMESTAMPTZ NOT NULL,
    frozen_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT dispatch_baselines_dispatch_fk
        FOREIGN KEY (organization_id, dispatch_id)
        REFERENCES dispatches (organization_id, id),
    CONSTRAINT dispatch_baselines_version_check CHECK (forecast_version > 0)
);

CREATE TABLE dispatch_baseline_points (
    dispatch_id UUID NOT NULL REFERENCES dispatch_baselines (dispatch_id),
    forecast_at TIMESTAMPTZ NOT NULL,
    baseline_grid_import_kw NUMERIC(14, 3) NOT NULL,
    PRIMARY KEY (dispatch_id, forecast_at),
    CONSTRAINT dispatch_baseline_points_value_check CHECK (baseline_grid_import_kw >= 0)
);

CREATE TABLE api_idempotency (
    organization_id UUID NOT NULL,
    operation VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    resource_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (organization_id, operation, idempotency_key),
    CONSTRAINT api_idempotency_resource_fk
        FOREIGN KEY (organization_id, resource_id)
        REFERENCES dispatches (organization_id, id)
);

CREATE FUNCTION reject_dispatch_input_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'dispatch execution inputs are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER dispatch_allocations_immutable
    BEFORE UPDATE OR DELETE ON dispatch_allocations
    FOR EACH ROW EXECUTE FUNCTION reject_dispatch_input_mutation();

CREATE TRIGGER dispatch_baselines_immutable
    BEFORE UPDATE OR DELETE ON dispatch_baselines
    FOR EACH ROW EXECUTE FUNCTION reject_dispatch_input_mutation();

CREATE TRIGGER dispatch_baseline_points_immutable
    BEFORE UPDATE OR DELETE ON dispatch_baseline_points
    FOR EACH ROW EXECUTE FUNCTION reject_dispatch_input_mutation();
