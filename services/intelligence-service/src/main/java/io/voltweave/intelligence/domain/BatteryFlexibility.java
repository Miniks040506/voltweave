package io.voltweave.intelligence.domain;

import java.time.Duration;

public record BatteryFlexibility(
        double capacityKwh,
        double currentSocPercent,
        double reserveSocPercent,
        double maxDischargePowerKw,
        double dischargeEfficiency,
        double siteExportHeadroomKw,
        boolean online,
        boolean telemetryFresh,
        boolean optedIn,
        boolean temperatureSafe,
        boolean reserved) {

    public BatteryFlexibility {
        requirePositive("capacityKwh", capacityKwh);
        requirePercent("currentSocPercent", currentSocPercent);
        requirePercent("reserveSocPercent", reserveSocPercent);
        requireNonNegative("maxDischargePowerKw", maxDischargePowerKw);
        requireEfficiency(dischargeEfficiency);
        requireNonNegative("siteExportHeadroomKw", siteExportHeadroomKw);
    }

    public double availablePowerKw(Duration dispatchDuration) {
        if (dispatchDuration == null || dispatchDuration.isZero() || dispatchDuration.isNegative()) {
            throw new IllegalArgumentException("dispatchDuration must be positive");
        }

        if (!online || !telemetryFresh || !optedIn || !temperatureSafe || reserved) {
            return 0.0;
        }

        double usableEnergyKwh = capacityKwh
                * Math.max(0.0, currentSocPercent - reserveSocPercent)
                / 100.0;
        double durationHours = (dispatchDuration.getSeconds()
                + dispatchDuration.getNano() / 1_000_000_000.0) / 3_600.0;
        double energyBoundPowerKw = usableEnergyKwh * dischargeEfficiency / durationHours;

        return Math.min(maxDischargePowerKw,
                Math.min(energyBoundPowerKw, siteExportHeadroomKw));
    }

    private static void requirePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static void requirePercent(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 100.0) {
            throw new IllegalArgumentException(name + " must be between 0 and 100");
        }
    }

    private static void requireEfficiency(double value) {
        if (!Double.isFinite(value) || value <= 0.0 || value > 1.0) {
            throw new IllegalArgumentException("dischargeEfficiency must be greater than 0 and at most 1");
        }
    }
}
