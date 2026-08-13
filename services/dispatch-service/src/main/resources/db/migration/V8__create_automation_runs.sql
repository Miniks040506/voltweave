CREATE TABLE automation_runs (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    vpp_id UUID NOT NULL,
    policy_id UUID NOT NULL,
    policy_version INTEGER NOT NULL,
    trigger_type VARCHAR(24) NOT NULL,
    approval_mode VARCHAR(24) NOT NULL,
    scheduled_start_at TIMESTAMPTZ NOT NULL,
    optimization_preview_id UUID NOT NULL,
    dispatch_id UUID NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT automation_runs_dispatch_fk
        FOREIGN KEY (organization_id, dispatch_id)
        REFERENCES dispatches (organization_id, id),
    CONSTRAINT automation_runs_policy_schedule_key
        UNIQUE (policy_id, policy_version, scheduled_start_at),
    CONSTRAINT automation_runs_trigger_check
        CHECK (trigger_type IN ('PEAK_LIMIT', 'PRICE_THRESHOLD')),
    CONSTRAINT automation_runs_approval_check
        CHECK (approval_mode IN ('REQUIRE_OPERATOR', 'AUTO_DISPATCH')),
    CONSTRAINT automation_runs_policy_version_check CHECK (policy_version > 0)
);

CREATE INDEX automation_runs_candidates_idx
    ON automation_runs (organization_id, vpp_id, evaluated_at DESC);
