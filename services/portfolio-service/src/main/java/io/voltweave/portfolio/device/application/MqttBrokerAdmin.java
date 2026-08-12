package io.voltweave.portfolio.device.application;

import io.voltweave.portfolio.device.application.model.MqttDeviceCredential;
import io.voltweave.portfolio.device.domain.entities.Device;

public interface MqttBrokerAdmin {
    MqttDeviceCredential provision(Device device);

    void revoke(String username);
}
