CREATE TABLE virtual_power_plants (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    region VARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT virtual_power_plants_organization_fk
        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT virtual_power_plants_org_id_key UNIQUE (organization_id, id),
    CONSTRAINT virtual_power_plants_org_name_key UNIQUE (organization_id, name),
    CONSTRAINT virtual_power_plants_name_check CHECK (length(btrim(name)) > 0),
    CONSTRAINT virtual_power_plants_region_check CHECK (length(btrim(region)) > 0),
    CONSTRAINT virtual_power_plants_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE vpp_memberships (
    id UUID PRIMARY KEY,
    vpp_organization_id UUID NOT NULL,
    site_organization_id UUID NOT NULL,
    vpp_id UUID NOT NULL,
    site_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    participation_weight NUMERIC(6, 3) NOT NULL DEFAULT 1,
    joined_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT vpp_memberships_vpp_fk
        FOREIGN KEY (vpp_organization_id, vpp_id)
        REFERENCES virtual_power_plants (organization_id, id),
    CONSTRAINT vpp_memberships_site_fk
        FOREIGN KEY (site_organization_id, site_id)
        REFERENCES sites (organization_id, id),
    CONSTRAINT vpp_memberships_pair_key UNIQUE (vpp_id, site_id),
    CONSTRAINT vpp_memberships_status_check CHECK (status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT vpp_memberships_weight_check CHECK (participation_weight > 0)
);

CREATE UNIQUE INDEX vpp_memberships_one_active_vpp_per_site
    ON vpp_memberships (site_id)
    WHERE status = 'ACTIVE';

CREATE INDEX vpp_memberships_active_vpp_idx
    ON vpp_memberships (vpp_organization_id, vpp_id, site_id)
    WHERE status = 'ACTIVE';
