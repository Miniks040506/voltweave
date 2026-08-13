package io.voltweave.dispatch.access;

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
public class InternalClientsConfiguration {
    @Bean
    OAuth2AuthorizedClientManager dispatchAuthorizedClientManager(
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
    RestClient dispatchPortfolioRestClient(
            OAuth2AuthorizedClientManager manager,
            @Value("${voltweave.portfolio.base-url}") String baseUrl
    ) {
        return restClient(manager, baseUrl);
    }

    @Bean
    RestClient dispatchIntelligenceRestClient(
            OAuth2AuthorizedClientManager manager,
            @Value("${voltweave.intelligence.base-url}") String baseUrl
    ) {
        return restClient(manager, baseUrl);
    }

    private static RestClient restClient(OAuth2AuthorizedClientManager manager, String baseUrl) {
        var oauth = new OAuth2ClientHttpRequestInterceptor(manager);
        oauth.setClientRegistrationIdResolver(request -> "voltweave-internal");
        oauth.setPrincipalResolver(request -> new UsernamePasswordAuthenticationToken(
                "dispatch-service", "N/A", List.of()
        ));
        return RestClient.builder().baseUrl(baseUrl).requestInterceptor(oauth).build();
    }
}
