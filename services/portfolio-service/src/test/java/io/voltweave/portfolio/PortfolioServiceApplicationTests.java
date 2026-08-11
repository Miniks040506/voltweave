package io.voltweave.portfolio;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class PortfolioServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
