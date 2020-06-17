package io.digital.patterns.workflow.security.cockpit;


import org.camunda.bpm.webapp.impl.security.auth.ContainerBasedAuthenticationFilter;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.web.context.request.RequestContextListener;

import java.util.Collections;


@Configuration
@EnableWebSecurity
@Order(SecurityProperties.BASIC_AUTH_ORDER - 10)
public class WebAppSecurityConfiguration extends WebSecurityConfigurerAdapter {

    private final KeycloakLogoutHandler keycloakLogoutHandler;

    private final JwtDecoder jwtDecoder;

    public WebAppSecurityConfiguration(KeycloakLogoutHandler keycloakLogoutHandler, JwtDecoder jwtDecoder) {
        this.keycloakLogoutHandler = keycloakLogoutHandler;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {

        http
                .csrf().ignoringAntMatchers("/api/**", "/engine-rest/**", "/webhook/**", "/actuator/**")
                .and()
                .antMatcher("/**")
                .authorizeRequests()
                .antMatchers("/app/**")
                .authenticated()
                .anyRequest()
                .permitAll()
                .and()
                .logout()
                .logoutRequestMatcher(new AntPathRequestMatcher("/app/**/logout"))
                .logoutSuccessHandler(keycloakLogoutHandler)
                .and()
                .oauth2Login().userInfoEndpoint().oidcUserService(new KeycloakOauth2UserService(jwtDecoder));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Bean
    public FilterRegistrationBean containerBasedAuthenticationFilter() {

        FilterRegistrationBean filterRegistration = new FilterRegistrationBean();
        filterRegistration.setFilter(new ContainerBasedAuthenticationFilter());
        filterRegistration.setInitParameters(Collections.singletonMap("authentication-provider",
                KeycloakAuthenticationProvider.class.getName()));
        filterRegistration.setOrder(101); // make sure the filter is registered after the Spring Security Filter Chain
        filterRegistration.addUrlPatterns("/app/*");
        return filterRegistration;
    }

    @Bean
    @Order(0)
    public RequestContextListener requestContextListener() {
        return new RequestContextListener();
    }

    @Bean
    public static ConfigureRedisAction configureRedisAction() {
        return ConfigureRedisAction.NO_OP;
    }

}