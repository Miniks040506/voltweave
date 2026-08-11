CREATE TABLE devices (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    site_id UUID NOT NULL,
    external_device_id VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL,
    manufacturer VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    rated_power_kw NUMERIC(12, 3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    communication_protocol VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT devices_site_fk
        FOREIGN KEY (organization_id, site_id)
        REFERENCES sites (organization_id, id),
    CONSTRAINT devices_org_id_key UNIQUE (organization_id, id),
    CONSTRAINT devices_external_id_key UNIQUE (organization_id, external_device_id),
    CONSTRAINT devices_external_id_check CHECK (length(btrim(external_device_id)) > 0),
    CONSTRAINT devices_manufacturer_check CHECK (length(btrim(manufacturer)) > 0),
    CONSTRAINT devices_model_check CHECK (length(btrim(model)) > 0),
    CONSTRAINT devices_rated_power_check CHECK (rated_power_kw > 0),
    CONSTRAINT devices_type_check
        CHECK (type IN ('SMART_METER', 'SOLAR_INVERTER', 'BATTERY', 'EV_CHARGER')),
    CONSTRAINT devices_status_check
        CHECK (status IN ('REGISTERED', 'PROVISIONING', 'PROVISIONED', 'DISABLED', 'RETIRED')),
    CONSTRAINT devices_protocol_check CHECK (communication_protocol = 'MQTT')
);

CREATE INDEX devices_site_idx ON devices (organization_id, site_id, id);
