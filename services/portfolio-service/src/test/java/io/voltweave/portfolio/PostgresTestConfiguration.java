package io.voltweave.portfolio;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.voltweave.portfolio.device.application.MqttBrokerAdmin;
import io.voltweave.portfolio.device.application.model.MqttDeviceCredential;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18.0-alpine");
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            throw new JwtException("JWT decoding is not used by persistence tests");
        };
    }

    @Bean
    @Primary
    MqttBrokerAdmin mqttBrokerAdmin() {
        return new MqttBrokerAdmin() {
            @Override
            public MqttDeviceCredential provision(
                    io.voltweave.portfolio.device.domain.entities.Device device
            ) {
                var identity = "device-" + device.id();
                var root = "voltweave/" + device.organizationId() + "/"
                        + device.siteId() + "/" + device.id();
                return new MqttDeviceCredential(
                        "tcp://mqtt.test:1883", identity, "one-time-test-password",
                        identity, root + "/telemetry", root + "/status",
                        root + "/ack", root + "/command"
                );
            }

            @Override
            public void revoke(String username) {
            }
        };
    }
}
