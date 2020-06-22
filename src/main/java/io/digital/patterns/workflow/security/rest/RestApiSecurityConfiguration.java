package io.digital.patterns.workflow.security.rest;

import org.camunda.bpm.engine.IdentityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.List;


@Configuration
@EnableWebSecurity
@Order(SecurityProperties.BASIC_AUTH_ORDER - 25)
@ConditionalOnProperty(name = "rest.security.enabled", havingValue = "true", matchIfMissing = true)
public class RestApiSecurityConfiguration extends WebSecurityConfigurerAdapter {

    private static final String ENGINE = "/engine";
    private static final String ACTUATOR_HEALTH = "/actuator/health";
    private static final String ACTUATOR_METRICS = "/actuator/metrics";
    private final IdentityService identityService;
    private final KeycloakAuthenticationConverter keycloakAuthenticationConverter;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuer;

    @Value("${camunda.bpmn.upload.roles:process_admin}")
    private List<String> bpmnUploadRoles;

    public RestApiSecurityConfiguration(IdentityService identityService,
                                        KeycloakAuthenticationConverter keycloakAuthenticationConverter) {
        this.identityService = identityService;
        this.keycloakAuthenticationConverter = keycloakAuthenticationConverter;
    }


    @Override
    public void configure(final HttpSecurity http) throws Exception {
        http
                .csrf().ignoringAntMatchers("/api/**", "/engine-rest/**", "/webhook/**", "/actuator/**")
                .and()
                .antMatcher("/engine-rest/**")
                .authorizeRequests()
                .antMatchers("/engine-rest/deployment/create")
                .hasAnyAuthority(bpmnUploadRoles.toArray(new String[]{}))
                .antMatchers(ENGINE).permitAll()
                .antMatchers(ACTUATOR_HEALTH).permitAll()
                .antMatchers(ACTUATOR_METRICS).permitAll()
                .anyRequest()
                .authenticated()
                .and()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and().oauth2ResourceServer()
                .jwt()
                .jwtAuthenticationConverter(keycloakAuthenticationConverter);
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
    public NimbusJwtDecoder jwtDecoder() {
        NimbusJwtDecoder jwtDecoder = (NimbusJwtDecoder)
                JwtDecoders.fromOidcIssuerLocation(issuer);

        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator("camunda-rest-api");
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);

        jwtDecoder.setJwtValidator(withAudience);

        return jwtDecoder;
    }

}