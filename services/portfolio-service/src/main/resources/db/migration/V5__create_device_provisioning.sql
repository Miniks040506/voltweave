CREATE TABLE device_provisioning_requests (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    device_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT device_provisioning_requests_device_fk
        FOREIGN KEY (organization_id, device_id)
        REFERENCES devices (organization_id, id),
    CONSTRAINT device_provisioning_requests_device_key UNIQUE (device_id),
    CONSTRAINT device_provisioning_requests_status_check CHECK (status = 'PENDING')
);

CREATE TABLE api_idempotency_records (
    organization_id UUID NOT NULL,
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    resource_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (organization_id, operation, idempotency_key),
    CONSTRAINT api_idempotency_records_organization_fk
        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT api_idempotency_records_operation_check CHECK (length(btrim(operation)) > 0),
    CONSTRAINT api_idempotency_records_key_check CHECK (length(btrim(idempotency_key)) > 0),
    CONSTRAINT api_idempotency_records_hash_check CHECK (request_hash ~ '^[0-9a-f]{64}$')
);
