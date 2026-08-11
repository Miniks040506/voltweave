CREATE TABLE battery_configurations (
    device_id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    capacity_kwh NUMERIC(12, 3) NOT NULL,
    max_charge_kw NUMERIC(12, 3) NOT NULL,
    max_discharge_kw NUMERIC(12, 3) NOT NULL,
    min_soc_percent SMALLINT NOT NULL,
    max_soc_percent SMALLINT NOT NULL,
    efficiency NUMERIC(6, 5) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT battery_configurations_device_fk
        FOREIGN KEY (organization_id, device_id)
        REFERENCES devices (organization_id, id),
    CONSTRAINT battery_configurations_power_check
        CHECK (capacity_kwh > 0 AND max_charge_kw > 0 AND max_discharge_kw > 0),
    CONSTRAINT battery_configurations_soc_check
        CHECK (min_soc_percent >= 0 AND max_soc_percent <= 100
            AND min_soc_percent < max_soc_percent),
    CONSTRAINT battery_configurations_efficiency_check
        CHECK (efficiency > 0 AND efficiency <= 1)
);

CREATE TABLE ev_charger_configurations (
    device_id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    max_charging_kw NUMERIC(12, 3) NOT NULL,
    vehicle_battery_capacity_kwh NUMERIC(12, 3) NOT NULL,
    target_soc_percent SMALLINT NOT NULL,
    charging_efficiency NUMERIC(6, 5) NOT NULL,
    departure_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ev_charger_configurations_device_fk
        FOREIGN KEY (organization_id, device_id)
        REFERENCES devices (organization_id, id),
    CONSTRAINT ev_charger_configurations_power_check
        CHECK (max_charging_kw > 0 AND vehicle_battery_capacity_kwh > 0),
    CONSTRAINT ev_charger_configurations_target_soc_check
        CHECK (target_soc_percent BETWEEN 1 AND 100),
    CONSTRAINT ev_charger_configurations_efficiency_check
        CHECK (charging_efficiency > 0 AND charging_efficiency <= 1)
);
