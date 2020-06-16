package io.digital.patterns.workflow.security.rest;

import org.camunda.bpm.engine.IdentityService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.redis.RedisTokenStore;


@Configuration
@EnableResourceServer
@EnableWebSecurity
@Order(SecurityProperties.BASIC_AUTH_ORDER - 20)
@ConditionalOnProperty(name = "rest.security.enabled", havingValue = "true", matchIfMissing = true)
public class RestApiSecurityConfiguration extends ResourceServerConfigurerAdapter {

    private static final String ENGINE = "/engine";
    private static final String ACTUATOR_HEALTH = "/actuator/health";
    private static final String ACTUATOR_METRICS = "/actuator/metrics";
    private final RestApiSecurityConfigurationProperties configProps;
    private final IdentityService identityService;
    private final RedisConnectionFactory redisConnectionFactory;

    public RestApiSecurityConfiguration(RestApiSecurityConfigurationProperties configProps,
                                        IdentityService identityService,
                                        RedisConnectionFactory redisConnectionFactory) {
        this.configProps = configProps;
        this.identityService = identityService;
        this.redisConnectionFactory = redisConnectionFactory;
    }


    @Override
    public void configure(final HttpSecurity http) throws Exception {
        http
                .csrf().ignoringAntMatchers("/api/**", "/engine-rest/**", "/webhook/**", "/actuator/**")
                .and()
                .antMatcher("/engine-rest/**")
                .authorizeRequests()
                .antMatchers(ENGINE).permitAll()
                .antMatchers(ACTUATOR_HEALTH).permitAll()
                .antMatchers(ACTUATOR_METRICS).permitAll()
                .anyRequest()
                .authenticated();
    }


    @Override
    public void configure(final ResourceServerSecurityConfigurer config) {
        config
                .tokenServices(tokenServices())
                .resourceId(configProps.getRequiredAudience());
    }

    public ResourceServerTokenServices tokenServices() {
        DefaultTokenServices defaultTokenServices = new DefaultTokenServices();
        defaultTokenServices.setTokenStore(tokenStore());
        return defaultTokenServices;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Bean
    public FilterRegistrationBean keycloakAuthenticationFilter(){
        FilterRegistrationBean filterRegistration = new FilterRegistrationBean();
        filterRegistration.setFilter(new KeycloakAuthenticationFilter(identityService));
        filterRegistration.setOrder(102);
        filterRegistration.addUrlPatterns("/engine-rest/*");
        return filterRegistration;
    }


    @Bean
    public TokenStore tokenStore() {
        return new RedisTokenStore(redisConnectionFactory);
    }
}