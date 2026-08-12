package io.voltweave.telemetry;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TimescaleTestConfiguration {
    @Bean
    @ServiceConnection
    PostgreSQLContainer timescale() {
        var image = DockerImageName.parse("timescale/timescaledb:2.29.0-pg18")
                .asCompatibleSubstituteFor("postgres");
        return new PostgreSQLContainer(image)
                .withDatabaseName("telemetry_test")
                .withUsername("telemetry_app")
                .withPassword("telemetry-test-password");
    }
}
