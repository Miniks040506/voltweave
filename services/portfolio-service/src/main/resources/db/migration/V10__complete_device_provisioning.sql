ALTER TABLE device_provisioning_requests
    DROP CONSTRAINT device_provisioning_requests_status_check;

ALTER TABLE device_provisioning_requests
    ADD COLUMN mqtt_username VARCHAR(128),
    ADD COLUMN mqtt_client_id VARCHAR(128),
    ADD COLUMN provisioned_at TIMESTAMPTZ,
    ADD COLUMN revoked_at TIMESTAMPTZ,
    ADD CONSTRAINT device_provisioning_requests_status_check
        CHECK (status IN ('PENDING', 'PROVISIONED', 'REVOKED')),
    ADD CONSTRAINT device_provisioning_requests_mqtt_username_key UNIQUE (mqtt_username),
    ADD CONSTRAINT device_provisioning_requests_mqtt_client_id_key UNIQUE (mqtt_client_id),
    ADD CONSTRAINT device_provisioning_requests_state_check CHECK (
        (status = 'PENDING'
            AND mqtt_username IS NULL
            AND mqtt_client_id IS NULL
            AND provisioned_at IS NULL
            AND revoked_at IS NULL)
        OR
        (status = 'PROVISIONED'
            AND mqtt_username IS NOT NULL
            AND mqtt_client_id IS NOT NULL
            AND provisioned_at IS NOT NULL
            AND revoked_at IS NULL)
        OR
        (status = 'REVOKED'
            AND mqtt_username IS NOT NULL
            AND mqtt_client_id IS NOT NULL
            AND provisioned_at IS NOT NULL
            AND revoked_at IS NOT NULL)
    );
