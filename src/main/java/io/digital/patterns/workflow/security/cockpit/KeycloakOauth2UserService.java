package io.digital.patterns.workflow.security.cockpit;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;


@RequiredArgsConstructor
@SuppressWarnings("unchecked")
public class KeycloakOauth2UserService extends OidcUserService {
    private static final OAuth2Error INVALID_REQUEST = new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST);

    private final JwtDecoder jwtDecoder;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser user = super.loadUser(userRequest);
        Collection<GrantedAuthority> keycloakAuthorities = extractKeycloakAuthorities(userRequest);
        return withAuthorities(user, keycloakAuthorities);
    }


    private OidcUser withAuthorities(OidcUser user, Collection<? extends GrantedAuthority> authorities) {
        Set<GrantedAuthority> newAuthorities = new LinkedHashSet<>();
        newAuthorities.addAll(authorities);
        return new DefaultOidcUser(
                newAuthorities,
                user.getIdToken(),
                user.getUserInfo(),
                "email"
        );
    }

    private Collection<GrantedAuthority> extractKeycloakAuthorities(OidcUserRequest userRequest) {
        Jwt token = parseJwt(userRequest.getAccessToken().getTokenValue());

        Map<String, Object> realmAccess = (Map<String, Object>) token.getClaims().get("realm_access");
        List<String> clientRoles = (List<String>) realmAccess.get("roles");
        if (CollectionUtils.isEmpty(clientRoles)) {
            return Collections.emptyList();
        }
        return clientRoles
                .stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

    }

    private Jwt parseJwt(String accessTokenValue) {
        try {
            return jwtDecoder.decode(accessTokenValue);
        } catch (JwtException e) {
            throw new OAuth2AuthenticationException(INVALID_REQUEST, e);
        }
    }
}
