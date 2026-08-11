CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    legal_name VARCHAR(160) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    tenant_code VARCHAR(63) NOT NULL,
    status VARCHAR(16) NOT NULL,
    country CHAR(2) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT organizations_type_check
        CHECK (type IN ('VPP_OPERATOR', 'COMMERCIAL_CUSTOMER', 'PLATFORM_INTERNAL')),
    CONSTRAINT organizations_status_check
        CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT organizations_legal_name_check
        CHECK (length(btrim(legal_name)) > 0),
    CONSTRAINT organizations_display_name_check
        CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT organizations_tenant_code_key UNIQUE (tenant_code),
    CONSTRAINT organizations_tenant_code_check
        CHECK (tenant_code ~ '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$'),
    CONSTRAINT organizations_country_check
        CHECK (country ~ '^[A-Z]{2}$'),
    CONSTRAINT organizations_timezone_check
        CHECK (length(btrim(timezone)) > 0)
);

CREATE TABLE organization_members (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT organization_members_organization_fk
        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT organization_members_subject_check
        CHECK (length(btrim(subject_id)) > 0),
    CONSTRAINT organization_members_role_check
        CHECK (role IN ('OWNER', 'MEMBER')),
    CONSTRAINT organization_members_status_check
        CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT organization_members_org_subject_key
        UNIQUE (organization_id, subject_id)
);

CREATE INDEX organization_members_subject_org_idx
    ON organization_members (subject_id, organization_id);
