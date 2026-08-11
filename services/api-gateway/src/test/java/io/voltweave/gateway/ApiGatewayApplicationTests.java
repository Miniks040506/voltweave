package io.voltweave.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayProperties;

@SpringBootTest
class ApiGatewayApplicationTests {
    @Autowired
    private GatewayProperties gatewayProperties;

    @Test
    void loadsPortfolioRoutes() {
        assertThat(gatewayProperties.getRoutes())
                .extracting("id")
                .containsExactly("portfolio-organizations", "portfolio-sites");
    }
}
