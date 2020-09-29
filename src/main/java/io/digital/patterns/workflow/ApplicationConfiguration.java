package io.digital.patterns.workflow;

import io.digital.patterns.workflow.security.KeycloakBearerTokenInterceptor;
import io.digital.patterns.workflow.security.KeycloakClient;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableRetry
public class ApplicationConfiguration {
    @Bean
    public RestTemplate restTemplate(KeycloakClient keycloakClient,
                                     RestTemplateBuilder builder) {
        KeycloakBearerTokenInterceptor keycloakBearerTokenInterceptor =
                new KeycloakBearerTokenInterceptor(keycloakClient);
        RestTemplate restTemplate = builder.build();
        restTemplate.getInterceptors().add(keycloakBearerTokenInterceptor);
        return restTemplate;
    }

    @Bean
    public OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }

    @Bean
    public RetryTemplate retryTemplate() {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(4);
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(3000);
        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(retryPolicy);
        template.setBackOffPolicy(backOffPolicy);
        return template;
    }

}
