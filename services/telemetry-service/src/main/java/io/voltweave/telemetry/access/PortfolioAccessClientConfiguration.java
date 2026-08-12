package io.voltweave.telemetry.access;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class PortfolioAccessClientConfiguration {

    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService clients
    ) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                registrations, clients
        );
        manager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build()
        );
        return manager;
    }

    @Bean
    RestClient portfolioAccessRestClient(
            RestClient.Builder builder,
            OAuth2AuthorizedClientManager authorizedClientManager,
            @Value("${voltweave.portfolio.base-url}") String baseUrl
    ) {
        var oauth = new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
        oauth.setClientRegistrationIdResolver(request -> "voltweave-internal");
        oauth.setPrincipalResolver(request -> new UsernamePasswordAuthenticationToken(
                "telemetry-service", "N/A", List.of()
        ));
        return builder.baseUrl(baseUrl).requestInterceptor(oauth).build();
    }
}
