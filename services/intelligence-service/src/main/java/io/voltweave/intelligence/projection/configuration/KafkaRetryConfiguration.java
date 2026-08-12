package io.voltweave.intelligence.projection.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
public class KafkaRetryConfiguration {
    @Bean
    DefaultErrorHandler intelligenceKafkaErrorHandler() {
        return new DefaultErrorHandler(
                new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS)
        );
    }
}
