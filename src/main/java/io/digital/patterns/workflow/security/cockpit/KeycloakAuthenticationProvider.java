package io.digital.patterns.workflow.security.cockpit;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.rest.security.auth.AuthenticationResult;
import org.camunda.bpm.engine.rest.security.auth.impl.ContainerBasedAuthenticationProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

public class KeycloakAuthenticationProvider extends ContainerBasedAuthenticationProvider {

    @Override
    public AuthenticationResult extractAuthenticatedUser(HttpServletRequest request, ProcessEngine engine) {

        OAuth2AuthenticationToken authentication = (OAuth2AuthenticationToken) SecurityContextHolder.getContext()
                .getAuthentication();
        if (authentication == null) {
            return AuthenticationResult.unsuccessful();
        }
        if ( authentication.getDetails() == null) {
            return AuthenticationResult.unsuccessful();
        }

        @SuppressWarnings("unchecked")
        String userId = authentication.getName();
        if (StringUtils.isEmpty(userId)) {
            return AuthenticationResult.unsuccessful();
        }

        AuthenticationResult authenticationResult = new AuthenticationResult(userId, true);
        authenticationResult.setGroups(getUserGroups(userId, engine));

        return authenticationResult;
    }

    private List<String> getUserGroups(String userId, ProcessEngine engine) {
        return engine.getIdentityService().createGroupQuery().groupMember(userId).list()
                .stream()
                .map(Group::getId).collect(Collectors.toList());
    }

}