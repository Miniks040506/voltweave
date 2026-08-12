package io.voltweave.simulator.domain;

public record DeviceActionResult(
        boolean accepted,
        double appliedPowerKw,
        String reason
) {
    public static DeviceActionResult accepted(double appliedPowerKw) {
        return new DeviceActionResult(true, appliedPowerKw, null);
    }

    public static DeviceActionResult rejected(double currentPowerKw, String reason) {
        return new DeviceActionResult(false, currentPowerKw, reason);
    }
}
