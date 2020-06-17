package io.digital.patterns.workflow.security.rest;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.identity.Group;

import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import javax.servlet.*;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class KeycloakAuthenticationFilter implements Filter {
    private IdentityService identityService;

    public KeycloakAuthenticationFilter(IdentityService identityService) {
        this.identityService = identityService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtAuthenticationToken details = (JwtAuthenticationToken) authentication;
        Jwt jwt = details.getToken();
        String userId =  jwt.getClaims().get("email").toString();
        log.debug("Extracted userId from bearer token: {}", userId);

        if (userId != null) {
            MDC.put("userId", userId);
        }
        try {
            identityService.setAuthentication(userId, getUserGroups(userId));
            chain.doFilter(request, response);
        } finally {
            identityService.clearAuthentication();
            MDC.remove("userId");
        }
    }

    private List<String> getUserGroups(String userId) {
        return identityService.createGroupQuery().groupMember(userId).list()
                .stream()
                .map(Group::getId).collect(Collectors.toList());
    }
}
