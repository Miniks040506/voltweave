CREATE TABLE flexibility_versions (
    vpp_organization_id UUID NOT NULL,
    vpp_id UUID NOT NULL,
    last_version BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (vpp_organization_id, vpp_id),
    CONSTRAINT flexibility_versions_positive_check CHECK (last_version > 0)
);

CREATE TABLE flexibility_snapshots (
    id UUID PRIMARY KEY,
    vpp_organization_id UUID NOT NULL,
    vpp_id UUID NOT NULL,
    version BIGINT NOT NULL,
    dispatch_duration_seconds BIGINT NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    upward_flexibility_kw NUMERIC(14, 3) NOT NULL,
    available_energy_kwh NUMERIC(14, 3) NOT NULL,
    CONSTRAINT flexibility_snapshots_org_id_key UNIQUE (vpp_organization_id, id),
    CONSTRAINT flexibility_snapshots_vpp_version_key
        UNIQUE (vpp_organization_id, vpp_id, version),
    CONSTRAINT flexibility_snapshots_version_check CHECK (version > 0),
    CONSTRAINT flexibility_snapshots_duration_check CHECK (dispatch_duration_seconds > 0),
    CONSTRAINT flexibility_snapshots_freshness_check CHECK (generated_at < valid_until),
    CONSTRAINT flexibility_snapshots_values_check
        CHECK (upward_flexibility_kw >= 0 AND available_energy_kwh >= 0)
);

CREATE INDEX flexibility_snapshots_latest_idx
    ON flexibility_snapshots (vpp_organization_id, vpp_id, version DESC);

CREATE TABLE flexibility_candidates (
    vpp_organization_id UUID NOT NULL,
    snapshot_id UUID NOT NULL,
    site_id UUID NOT NULL,
    device_id UUID NOT NULL,
    device_type VARCHAR(32) NOT NULL,
    raw_upward_flexibility_kw NUMERIC(14, 3) NOT NULL,
    upward_flexibility_kw NUMERIC(14, 3) NOT NULL,
    available_energy_kwh NUMERIC(14, 3) NOT NULL,
    limiting_reason VARCHAR(80),
    PRIMARY KEY (snapshot_id, device_id),
    CONSTRAINT flexibility_candidates_snapshot_fk
        FOREIGN KEY (vpp_organization_id, snapshot_id)
        REFERENCES flexibility_snapshots (vpp_organization_id, id),
    CONSTRAINT flexibility_candidates_device_type_check
        CHECK (device_type IN ('BATTERY', 'EV_CHARGER')),
    CONSTRAINT flexibility_candidates_values_check
        CHECK (raw_upward_flexibility_kw >= 0
           AND upward_flexibility_kw >= 0
           AND upward_flexibility_kw <= raw_upward_flexibility_kw
           AND available_energy_kwh >= 0)
);

CREATE FUNCTION reject_flexibility_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'flexibility snapshots are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER flexibility_snapshots_immutable
    BEFORE UPDATE OR DELETE ON flexibility_snapshots
    FOR EACH ROW EXECUTE FUNCTION reject_flexibility_mutation();

CREATE TRIGGER flexibility_candidates_immutable
    BEFORE UPDATE OR DELETE ON flexibility_candidates
    FOR EACH ROW EXECUTE FUNCTION reject_flexibility_mutation();
