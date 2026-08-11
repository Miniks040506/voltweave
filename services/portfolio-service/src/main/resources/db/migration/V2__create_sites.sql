CREATE TABLE sites (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    region VARCHAR(120) NOT NULL,
    country CHAR(2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT sites_organization_fk
        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT sites_org_id_key UNIQUE (organization_id, id),
    CONSTRAINT sites_name_check CHECK (length(btrim(name)) > 0),
    CONSTRAINT sites_timezone_check CHECK (length(btrim(timezone)) > 0),
    CONSTRAINT sites_region_check CHECK (length(btrim(region)) > 0),
    CONSTRAINT sites_country_check CHECK (country ~ '^[A-Z]{2}$'),
    CONSTRAINT sites_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE site_preferences (
    site_id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    vpp_opt_in BOOLEAN NOT NULL DEFAULT FALSE,
    minimum_battery_reserve_percent SMALLINT NOT NULL DEFAULT 20,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT site_preferences_site_fk
        FOREIGN KEY (organization_id, site_id)
        REFERENCES sites (organization_id, id),
    CONSTRAINT site_preferences_reserve_check
        CHECK (minimum_battery_reserve_percent BETWEEN 0 AND 100)
);
