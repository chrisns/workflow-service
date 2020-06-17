package io.digital.patterns.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.digital.patterns.workflow.security.KeycloakBearerTokenInterceptor;
import io.digital.patterns.workflow.security.KeycloakClient;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.client.RestTemplate;

@Configuration
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

}
