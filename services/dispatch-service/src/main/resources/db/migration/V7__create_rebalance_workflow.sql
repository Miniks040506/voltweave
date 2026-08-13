ALTER TABLE dispatches
    ADD COLUMN under_delivery_since TIMESTAMPTZ,
    ADD COLUMN last_rebalance_at TIMESTAMPTZ,
    ADD COLUMN rebalance_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT dispatches_rebalance_count_check CHECK (rebalance_count >= 0);

ALTER TABLE dispatch_performance_points
    DROP CONSTRAINT dispatch_performance_allocation_fk;

CREATE TABLE dispatch_rebalances (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    dispatch_id UUID NOT NULL,
    optimization_preview_id UUID,
    missing_power_kw NUMERIC(14, 3) NOT NULL,
    planned_power_kw NUMERIC(14, 3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT dispatch_rebalances_dispatch_fk
        FOREIGN KEY (organization_id, dispatch_id)
        REFERENCES dispatches (organization_id, id),
    CONSTRAINT dispatch_rebalances_values_check CHECK (
        missing_power_kw > 0 AND planned_power_kw >= 0
    ),
    CONSTRAINT dispatch_rebalances_status_check CHECK (
        status IN ('COMMANDING', 'FAILED')
    )
);

CREATE INDEX dispatch_rebalances_timeline_idx
    ON dispatch_rebalances (organization_id, dispatch_id, started_at);

CREATE TABLE dispatch_replacement_allocations (
    organization_id UUID NOT NULL,
    dispatch_id UUID NOT NULL,
    rebalance_id UUID NOT NULL REFERENCES dispatch_rebalances (id),
    site_id UUID NOT NULL,
    device_id UUID NOT NULL,
    device_type VARCHAR(32) NOT NULL,
    source_available_power_kw NUMERIC(14, 3) NOT NULL,
    allocated_power_kw NUMERIC(14, 3) NOT NULL,
    expected_energy_kwh NUMERIC(14, 3) NOT NULL,
    score NUMERIC(5, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (dispatch_id, device_id),
    CONSTRAINT dispatch_replacement_allocations_dispatch_fk
        FOREIGN KEY (organization_id, dispatch_id)
        REFERENCES dispatches (organization_id, id),
    CONSTRAINT dispatch_replacement_allocations_values_check CHECK (
        source_available_power_kw > 0
        AND source_available_power_kw >= allocated_power_kw
        AND allocated_power_kw > 0
        AND expected_energy_kwh > 0
        AND score BETWEEN 0 AND 1
    )
);

