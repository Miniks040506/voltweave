ALTER TABLE telemetry_points
    ADD COLUMN telemetry_quality VARCHAR(16) NOT NULL DEFAULT 'VALID';

ALTER TABLE telemetry_points
    ALTER COLUMN telemetry_quality DROP DEFAULT,
    ADD CONSTRAINT telemetry_points_quality_check CHECK (
        telemetry_quality IN ('VALID', 'STALE', 'OUT_OF_ORDER')
    );

ALTER TABLE device_twins
    ADD COLUMN last_received_at TIMESTAMPTZ,
    ADD COLUMN telemetry_quality VARCHAR(16) NOT NULL DEFAULT 'VALID';

UPDATE device_twins
SET last_received_at = updated_at
WHERE last_received_at IS NULL;

ALTER TABLE device_twins
    ALTER COLUMN last_received_at SET NOT NULL,
    ALTER COLUMN telemetry_quality DROP DEFAULT,
    ADD CONSTRAINT device_twins_received_time_check CHECK (
        last_received_at >= last_observed_at
    ),
    ADD CONSTRAINT device_twins_quality_check CHECK (
        telemetry_quality IN ('VALID', 'STALE', 'OUT_OF_ORDER')
    );
