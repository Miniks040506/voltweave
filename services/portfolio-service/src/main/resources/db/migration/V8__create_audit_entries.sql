CREATE TABLE audit_entries (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id UUID,
    ip_address INET,
    user_agent VARCHAR(512),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT audit_entries_organization_fk
        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT audit_entries_actor_type_check
        CHECK (actor_type IN ('USER', 'SERVICE')),
    CONSTRAINT audit_entries_actor_id_check
        CHECK (length(btrim(actor_id)) > 0),
    CONSTRAINT audit_entries_action_check
        CHECK (length(btrim(action)) > 0),
    CONSTRAINT audit_entries_resource_type_check
        CHECK (length(btrim(resource_type)) > 0),
    CONSTRAINT audit_entries_metadata_object_check
        CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE INDEX audit_entries_org_time_idx
    ON audit_entries (organization_id, occurred_at DESC, id DESC);

CREATE FUNCTION reject_audit_entry_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit entries are append-only';
END;
$$;

CREATE TRIGGER audit_entries_reject_update_delete
BEFORE UPDATE OR DELETE ON audit_entries
FOR EACH ROW EXECUTE FUNCTION reject_audit_entry_mutation();
