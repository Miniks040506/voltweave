package io.voltweave.portfolio.device.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.voltweave.portfolio.device.domain.enums.DeviceLifecycleStatus;
import io.voltweave.portfolio.device.domain.enums.DeviceType;

class DeviceTests {
    @Test
    void registersNormalizedMqttDevice() {
        var device = device();

        assertThat(device.externalDeviceId()).isEqualTo("battery-01");
        assertThat(device.status()).isEqualTo(DeviceLifecycleStatus.REGISTERED);
        assertThat(device.communicationProtocol().name()).isEqualTo("MQTT");
    }

    @Test
    void beginsProvisioningOnlyOnce() {
        var provisioning = device().beginProvisioning(Instant.parse("2026-08-12T01:00:00Z"));

        assertThat(provisioning.status()).isEqualTo(DeviceLifecycleStatus.PROVISIONING);
        assertThatThrownBy(() -> provisioning.beginProvisioning(Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsNonPositiveRatedPower() {
        assertThatThrownBy(() -> Device.registered(
                UUID.randomUUID(), UUID.randomUUID(), "meter-01", DeviceType.SMART_METER,
                "VoltWeave", "Meter", BigDecimal.ZERO, Instant.now()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ratedPowerKw must be positive");
    }

    private Device device() {
        return Device.registered(
                UUID.randomUUID(), UUID.randomUUID(), "  battery-01  ", DeviceType.BATTERY,
                "VoltWeave", "Battery Simulator", new BigDecimal("5.000"),
                Instant.parse("2026-08-12T00:00:00Z")
        );
    }
}
