package io.digital.patterns.workflow.security.cockpit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


@Service
@Slf4j
public class KeycloakLogoutHandler implements LogoutSuccessHandler {

    private RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    private String oauth2UserLogoutUri;

    public KeycloakLogoutHandler(@Value("${spring.security.oauth2.client.provider.keycloak.authorization-uri:}")
                                         String oauth2UserAuthorizationUri) {
        if (!StringUtils.isEmpty(oauth2UserAuthorizationUri)) {
            this.oauth2UserLogoutUri = oauth2UserAuthorizationUri.replace("openid-connect/auth", "openid-connect/logout");
        }
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        if (!StringUtils.isEmpty(oauth2UserLogoutUri)) {
            String requestUrl = request.getRequestURL().toString();
            String redirectUri = requestUrl.substring(0, requestUrl.indexOf("/app"));

            String logoutUrl = oauth2UserLogoutUri + "?redirect_uri=" + redirectUri;
            log.debug("Redirecting to logout URL {}", logoutUrl);
            redirectStrategy.sendRedirect(request, response, logoutUrl);
        }
    }

}