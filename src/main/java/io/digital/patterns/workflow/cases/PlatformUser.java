package io.digital.patterns.workflow.cases;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static java.util.stream.Collectors.toList;

@Getter
public class PlatformUser {

    private final String email;
    private final List<String> roles;
    private final List<String> groups;

    public PlatformUser(JwtAuthenticationToken jwtAuthenticationToken) {
        this.email = jwtAuthenticationToken.getName();
        this.roles = jwtAuthenticationToken.getAuthorities()
                .stream().map(GrantedAuthority::getAuthority).collect(toList());
        this.groups = jwtAuthenticationToken.getToken().getClaim("groups");
    }

}
