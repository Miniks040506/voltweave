CREATE TABLE optimization_versions (
    vpp_organization_id UUID NOT NULL,
    vpp_id UUID NOT NULL,
    last_version BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (vpp_organization_id, vpp_id),
    CONSTRAINT optimization_versions_positive_check CHECK (last_version > 0)
);

CREATE TABLE optimization_previews (
    id UUID PRIMARY KEY,
    vpp_organization_id UUID NOT NULL,
    vpp_id UUID NOT NULL,
    version BIGINT NOT NULL,
    flexibility_snapshot_id UUID NOT NULL,
    flexibility_snapshot_version BIGINT NOT NULL,
    target_power_kw NUMERIC(14, 3) NOT NULL,
    reserve_margin_percent NUMERIC(6, 3) NOT NULL,
    required_power_kw NUMERIC(14, 3) NOT NULL,
    planned_power_kw NUMERIC(14, 3) NOT NULL,
    feasible BOOLEAN NOT NULL,
    weight_version VARCHAR(32) NOT NULL,
    reliability_weight NUMERIC(5, 4) NOT NULL,
    available_soc_weight NUMERIC(5, 4) NOT NULL,
    response_speed_weight NUMERIC(5, 4) NOT NULL,
    low_degradation_cost_weight NUMERIC(5, 4) NOT NULL,
    customer_preference_weight NUMERIC(5, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT optimization_previews_org_id_key UNIQUE (vpp_organization_id, id),
    CONSTRAINT optimization_previews_vpp_version_key
        UNIQUE (vpp_organization_id, vpp_id, version),
    CONSTRAINT optimization_previews_snapshot_fk
        FOREIGN KEY (vpp_organization_id, flexibility_snapshot_id)
        REFERENCES flexibility_snapshots (vpp_organization_id, id),
    CONSTRAINT optimization_previews_values_check CHECK (
        version > 0
        AND flexibility_snapshot_version > 0
        AND target_power_kw > 0
        AND reserve_margin_percent >= 0
        AND required_power_kw >= target_power_kw
        AND planned_power_kw >= 0
    ),
    CONSTRAINT optimization_previews_weights_check CHECK (
        reliability_weight + available_soc_weight + response_speed_weight
        + low_degradation_cost_weight + customer_preference_weight = 1
    )
);

CREATE INDEX optimization_previews_latest_idx
    ON optimization_previews (vpp_organization_id, vpp_id, version DESC);

CREATE TABLE optimization_candidates (
    vpp_organization_id UUID NOT NULL,
    preview_id UUID NOT NULL,
    site_id UUID NOT NULL,
    device_id UUID NOT NULL,
    device_type VARCHAR(32) NOT NULL,
    available_power_kw NUMERIC(14, 3) NOT NULL,
    available_energy_kwh NUMERIC(14, 3) NOT NULL,
    reliability NUMERIC(5, 4) NOT NULL,
    available_soc NUMERIC(5, 4) NOT NULL,
    response_speed NUMERIC(5, 4) NOT NULL,
    low_degradation_cost NUMERIC(5, 4) NOT NULL,
    customer_preference NUMERIC(5, 4) NOT NULL,
    score NUMERIC(5, 4) NOT NULL,
    allocated_power_kw NUMERIC(14, 3) NOT NULL,
    eligible BOOLEAN NOT NULL,
    PRIMARY KEY (preview_id, device_id),
    CONSTRAINT optimization_candidates_preview_fk
        FOREIGN KEY (vpp_organization_id, preview_id)
        REFERENCES optimization_previews (vpp_organization_id, id),
    CONSTRAINT optimization_candidates_values_check CHECK (
        available_power_kw >= 0
        AND available_energy_kwh >= 0
        AND allocated_power_kw >= 0
        AND allocated_power_kw <= available_power_kw
        AND reliability BETWEEN 0 AND 1
        AND available_soc BETWEEN 0 AND 1
        AND response_speed BETWEEN 0 AND 1
        AND low_degradation_cost BETWEEN 0 AND 1
        AND customer_preference BETWEEN 0 AND 1
        AND score BETWEEN 0 AND 1
    )
);

CREATE FUNCTION reject_optimization_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'optimization previews are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER optimization_previews_immutable
    BEFORE UPDATE OR DELETE ON optimization_previews
    FOR EACH ROW EXECUTE FUNCTION reject_optimization_mutation();

CREATE TRIGGER optimization_candidates_immutable
    BEFORE UPDATE OR DELETE ON optimization_candidates
    FOR EACH ROW EXECUTE FUNCTION reject_optimization_mutation();
