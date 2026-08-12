CREATE TABLE event_inbox (
    consumer_name VARCHAR(80) NOT NULL,
    event_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE vpp_site_projection (
    vpp_organization_id UUID NOT NULL,
    vpp_id UUID NOT NULL,
    site_id UUID NOT NULL,
    active BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (vpp_id, site_id),
    CONSTRAINT vpp_site_projection_org_vpp_key
        UNIQUE (vpp_organization_id, vpp_id, site_id)
);

CREATE INDEX vpp_site_projection_active_idx
    ON vpp_site_projection (vpp_organization_id, vpp_id, site_id)
    WHERE active;

CREATE TABLE energy_observations (
    organization_id UUID NOT NULL,
    site_id UUID NOT NULL,
    device_id UUID NOT NULL,
    sequence_number BIGINT NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    energy_type VARCHAR(24) NOT NULL,
    power_kw NUMERIC(14, 3) NOT NULL,
    quality VARCHAR(16) NOT NULL,
    PRIMARY KEY (device_id, observed_at, sequence_number),
    CONSTRAINT energy_observations_sequence_check CHECK (sequence_number >= 0),
    CONSTRAINT energy_observations_type_check
        CHECK (energy_type IN ('GRID_IMPORT', 'SOLAR_GENERATION')),
    CONSTRAINT energy_observations_quality_check
        CHECK (quality IN ('VALID', 'STALE', 'OUT_OF_ORDER')),
    CONSTRAINT energy_observations_solar_check
        CHECK (energy_type <> 'SOLAR_GENERATION' OR power_kw >= 0)
);

CREATE INDEX energy_observations_training_idx
    ON energy_observations (site_id, energy_type, observed_at DESC);

CREATE TABLE forecast_versions (
    vpp_organization_id UUID NOT NULL,
    vpp_id UUID NOT NULL,
    last_version BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (vpp_organization_id, vpp_id),
    CONSTRAINT forecast_versions_positive_check CHECK (last_version > 0)
);

CREATE TABLE forecasts (
    id UUID PRIMARY KEY,
    vpp_organization_id UUID NOT NULL,
    vpp_id UUID NOT NULL,
    version BIGINT NOT NULL,
    horizon VARCHAR(16) NOT NULL,
    model_name VARCHAR(80) NOT NULL,
    model_version VARCHAR(32) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    training_from TIMESTAMPTZ NOT NULL,
    training_to TIMESTAMPTZ NOT NULL,
    target_start TIMESTAMPTZ NOT NULL,
    target_end TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    CONSTRAINT forecasts_org_id_key UNIQUE (vpp_organization_id, id),
    CONSTRAINT forecasts_vpp_version_key
        UNIQUE (vpp_organization_id, vpp_id, version),
    CONSTRAINT forecasts_version_check CHECK (version > 0),
    CONSTRAINT forecasts_horizon_check
        CHECK (horizon IN ('MINUTES_15', 'HOUR_1', 'DAY_AHEAD')),
    CONSTRAINT forecasts_training_check CHECK (training_from < training_to),
    CONSTRAINT forecasts_target_check CHECK (target_start < target_end),
    CONSTRAINT forecasts_freshness_check CHECK (generated_at < valid_until)
);

CREATE INDEX forecasts_latest_idx
    ON forecasts (vpp_organization_id, vpp_id, version DESC);

CREATE TABLE forecast_points (
    vpp_organization_id UUID NOT NULL,
    forecast_id UUID NOT NULL,
    forecast_at TIMESTAMPTZ NOT NULL,
    baseline_grid_import_kw NUMERIC(14, 3) NOT NULL,
    solar_generation_kw NUMERIC(14, 3) NOT NULL,
    PRIMARY KEY (forecast_id, forecast_at),
    CONSTRAINT forecast_points_forecast_fk
        FOREIGN KEY (vpp_organization_id, forecast_id)
        REFERENCES forecasts (vpp_organization_id, id),
    CONSTRAINT forecast_points_solar_check CHECK (solar_generation_kw >= 0)
);

CREATE FUNCTION reject_forecast_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'forecast baselines are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER forecasts_immutable
    BEFORE UPDATE OR DELETE ON forecasts
    FOR EACH ROW EXECUTE FUNCTION reject_forecast_mutation();

CREATE TRIGGER forecast_points_immutable
    BEFORE UPDATE OR DELETE ON forecast_points
    FOR EACH ROW EXECUTE FUNCTION reject_forecast_mutation();
