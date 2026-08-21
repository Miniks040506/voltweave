package io.voltweave.simulator.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import io.voltweave.simulator.config.DeviceScenario;
import io.voltweave.simulator.config.DeviceType;
import io.voltweave.simulator.state.SimulatorState;

public final class SimulatedDevice {
    private final DeviceScenario scenario;
    private long sequenceNumber;
    private double activePowerKw;
    private double socPercent;

    public SimulatedDevice(DeviceScenario scenario) {
        this(scenario, null);
    }

    public SimulatedDevice(DeviceScenario scenario, SimulatorState restored) {
        this.scenario = scenario;
        if (restored == null) {
            this.socPercent = scenario.initialSocPercent();
            this.activePowerKw = scenario.type() == DeviceType.EV_CHARGER
                    ? scenario.ratedPowerKw() * 0.6 : 0;
            return;
        }
        if (!scenario.deviceId().equals(restored.deviceId())
                || scenario.type() != restored.deviceType()) {
            throw new IllegalArgumentException("saved state does not match device scenario");
        }
        this.sequenceNumber = restored.sequenceNumber();
        this.activePowerKw = restored.activePowerKw();
        this.socPercent = restored.socPercent();
    }

    public DeviceScenario scenario() {
        return scenario;
    }

    public synchronized DeviceTelemetry sample(Instant observedAt, Duration elapsed) {
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed cannot be negative");
        }
        integrateSoc(elapsed);
        activePowerKw = switch (scenario.type()) {
            case SMART_METER -> meterPower(observedAt);
            case SOLAR_INVERTER -> solarPower(observedAt);
            case BATTERY, EV_CHARGER -> constrainedPower(activePowerKw);
        };
        sequenceNumber++;
        return new DeviceTelemetry(
                scenario.deviceId(), sequenceNumber, observedAt, scenario.type(),
                round(activePowerKw), hasSoc() ? round(socPercent) : null, true
        );
    }

    public synchronized DeviceActionResult setPower(double requestedPowerKw) {
        if (!Double.isFinite(requestedPowerKw)) {
            return DeviceActionResult.rejected(activePowerKw, "power must be finite");
        }
        return switch (scenario.type()) {
            case SMART_METER, SOLAR_INVERTER -> DeviceActionResult.rejected(
                    activePowerKw, "device does not support power commands"
            );
            case EV_CHARGER -> setEvPower(requestedPowerKw);
            case BATTERY -> setBatteryPower(requestedPowerKw);
        };
    }

    public synchronized SimulatorState snapshot(
            UUID activeCommandId,
            Instant activeCommandExpiresAt,
            List<SimulatorState.AcknowledgementState> acknowledgements
    ) {
        return new SimulatorState(
                scenario.deviceId(), scenario.type(), sequenceNumber,
                activePowerKw, socPercent, activeCommandId,
                activeCommandExpiresAt, acknowledgements
        );
    }

    private DeviceActionResult setBatteryPower(double requestedPowerKw) {
        if (requestedPowerKw < -scenario.ratedPowerKw()
                || requestedPowerKw > scenario.ratedPowerKw()) {
            return DeviceActionResult.rejected(activePowerKw, "power exceeds battery rating");
        }
        if (requestedPowerKw < 0 && socPercent <= scenario.minSocPercent()) {
            return DeviceActionResult.rejected(activePowerKw, "battery reserve reached");
        }
        if (requestedPowerKw > 0 && socPercent >= scenario.maxSocPercent()) {
            return DeviceActionResult.rejected(activePowerKw, "battery maximum SOC reached");
        }
        activePowerKw = requestedPowerKw;
        return DeviceActionResult.accepted(activePowerKw);
    }

    private DeviceActionResult setEvPower(double requestedPowerKw) {
        if (requestedPowerKw < 0 || requestedPowerKw > scenario.ratedPowerKw()) {
            return DeviceActionResult.rejected(activePowerKw, "EV charging power is out of range");
        }
        if (requestedPowerKw > 0 && socPercent >= scenario.maxSocPercent()) {
            return DeviceActionResult.rejected(activePowerKw, "EV target SOC reached");
        }
        activePowerKw = requestedPowerKw;
        return DeviceActionResult.accepted(activePowerKw);
    }

    private void integrateSoc(Duration elapsed) {
        if (!hasSoc() || elapsed.isZero()) {
            return;
        }
        double hours = elapsed.toMillis() / 3_600_000.0;
        double energyKwh = activePowerKw >= 0
                ? activePowerKw * hours * scenario.efficiency()
                : activePowerKw * hours / scenario.efficiency();
        socPercent = clamp(
                socPercent + energyKwh / scenario.capacityKwh() * 100,
                scenario.minSocPercent(), scenario.maxSocPercent()
        );
        if ((socPercent <= scenario.minSocPercent() && activePowerKw < 0)
                || (socPercent >= scenario.maxSocPercent() && activePowerKw > 0)) {
            activePowerKw = 0;
        }
    }

    private double constrainedPower(double powerKw) {
        if (scenario.type() == DeviceType.EV_CHARGER) {
            return clamp(powerKw, 0, scenario.ratedPowerKw());
        }
        return clamp(powerKw, -scenario.ratedPowerKw(), scenario.ratedPowerKw());
    }

    private double meterPower(Instant observedAt) {
        double hour = observedAt.atZone(ZoneOffset.UTC).getHour()
                + observedAt.atZone(ZoneOffset.UTC).getMinute() / 60.0;
        double eveningPeak = Math.max(0, Math.sin(Math.PI * (hour - 15) / 8));
        return scenario.ratedPowerKw() * clamp(
                0.30 + 0.50 * eveningPeak + noise(), 0.10, 1.0
        );
    }

    private double solarPower(Instant observedAt) {
        double hour = observedAt.atZone(ZoneOffset.UTC).getHour()
                + observedAt.atZone(ZoneOffset.UTC).getMinute() / 60.0;
        double daylight = Math.max(0, Math.sin(Math.PI * (hour - 6) / 12));
        double cloudFactor = clamp(0.88 + noise(), 0.70, 1.0);
        return -scenario.ratedPowerKw() * daylight * cloudFactor;
    }

    private double noise() {
        long value = scenario.seed() + sequenceNumber * 0x9E3779B97F4A7C15L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        return ((value >>> 11) * 0x1.0p-53 - 0.5) * 0.10;
    }

    private boolean hasSoc() {
        return scenario.type() == DeviceType.BATTERY
                || scenario.type() == DeviceType.EV_CHARGER;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
