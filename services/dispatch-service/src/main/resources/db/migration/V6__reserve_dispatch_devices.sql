CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE device_reservations (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    dispatch_id UUID NOT NULL,
    device_id UUID NOT NULL,
    reserved_from TIMESTAMPTZ NOT NULL,
    reserved_until TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT device_reservations_dispatch_fk
        FOREIGN KEY (organization_id, dispatch_id)
        REFERENCES dispatches (organization_id, id),
    CONSTRAINT device_reservations_interval_check CHECK (reserved_from < reserved_until),
    CONSTRAINT device_reservations_dispatch_device_key UNIQUE (dispatch_id, device_id),
    CONSTRAINT device_reservations_no_overlap EXCLUDE USING gist (
        device_id WITH =,
        tstzrange(reserved_from, reserved_until, '[)') WITH &&
    )
);

CREATE INDEX device_reservations_dispatch_idx
    ON device_reservations (organization_id, dispatch_id);

