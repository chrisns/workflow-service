package io.digital.patterns.workflow.security.cockpit;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;


@Service
@Slf4j
public class KeycloakLogoutHandler implements LogoutSuccessHandler {


    private RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    private String oauth2UserLogoutUri;

    public KeycloakLogoutHandler(@Value("${security.oauth2.client.user-authorization-uri:}") String oauth2UserAuthorizationUri) {
        if (!StringUtils.isEmpty(oauth2UserAuthorizationUri)) {
            // in order to get the valid logout uri: simply replace "/auth" at the end of the user authorization uri with "/logout"
            this.oauth2UserLogoutUri = oauth2UserAuthorizationUri.replace("openid-connect/auth", "openid-connect/logout");
        }
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        if (!StringUtils.isEmpty(oauth2UserLogoutUri)) {
            String requestUrl = request.getRequestURL().toString();
            String redirectUri = requestUrl.substring(0, requestUrl.indexOf("/app")) + "/login";

            String logoutUrl = oauth2UserLogoutUri + "?redirect_uri=" + redirectUri;
            log.debug("Redirecting to logout URL {}", logoutUrl);
            redirectStrategy.sendRedirect(request, response, logoutUrl);
        }
    }

}