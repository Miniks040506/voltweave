CREATE TABLE automation_policies (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    vpp_id UUID NOT NULL,
    enabled BOOLEAN NOT NULL,
    trigger_type VARCHAR(24) NOT NULL,
    approval_mode VARCHAR(24) NOT NULL,
    peak_import_limit_kw NUMERIC(12, 3),
    price_threshold NUMERIC(12, 6),
    reserve_margin_percent SMALLINT NOT NULL,
    max_dispatch_power_kw NUMERIC(12, 3) NOT NULL,
    max_dispatch_duration_minutes INTEGER NOT NULL,
    under_delivery_tolerance_percent SMALLINT NOT NULL,
    under_delivery_grace_seconds INTEGER NOT NULL,
    rebalance_cooldown_seconds INTEGER NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    version INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT automation_policies_vpp_fk
        FOREIGN KEY (organization_id, vpp_id)
        REFERENCES virtual_power_plants (organization_id, id),
    CONSTRAINT automation_policies_vpp_key UNIQUE (vpp_id),
    CONSTRAINT automation_policies_trigger_check
        CHECK (trigger_type IN ('MANUAL', 'PEAK_LIMIT', 'PRICE_THRESHOLD')),
    CONSTRAINT automation_policies_approval_check
        CHECK (approval_mode IN ('REQUIRE_OPERATOR', 'AUTO_DISPATCH')),
    CONSTRAINT automation_policies_threshold_check CHECK (
        (trigger_type = 'MANUAL' AND peak_import_limit_kw IS NULL
            AND price_threshold IS NULL)
        OR (trigger_type = 'PEAK_LIMIT' AND peak_import_limit_kw > 0
            AND price_threshold IS NULL)
        OR (trigger_type = 'PRICE_THRESHOLD' AND peak_import_limit_kw IS NULL
            AND price_threshold > 0)
    ),
    CONSTRAINT automation_policies_margin_check
        CHECK (reserve_margin_percent BETWEEN 0 AND 100),
    CONSTRAINT automation_policies_power_check CHECK (max_dispatch_power_kw > 0),
    CONSTRAINT automation_policies_duration_check
        CHECK (max_dispatch_duration_minutes BETWEEN 1 AND 1440),
    CONSTRAINT automation_policies_tolerance_check
        CHECK (under_delivery_tolerance_percent BETWEEN 0 AND 100),
    CONSTRAINT automation_policies_recovery_check
        CHECK (under_delivery_grace_seconds >= 0 AND rebalance_cooldown_seconds >= 0),
    CONSTRAINT automation_policies_version_check CHECK (version > 0)
);
