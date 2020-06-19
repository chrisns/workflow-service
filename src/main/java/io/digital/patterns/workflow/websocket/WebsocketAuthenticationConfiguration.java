package io.digital.patterns.workflow.websocket;

import io.digital.patterns.workflow.security.rest.KeycloakAuthenticationConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
@Slf4j
public class WebsocketAuthenticationConfiguration implements WebSocketMessageBrokerConfigurer {

    private final JwtDecoder jwtDecoder;
    private final KeycloakAuthenticationConverter keycloakAuthenticationConverter;

    public WebsocketAuthenticationConfiguration(JwtDecoder jwtDecoder,
                                                KeycloakAuthenticationConverter keycloakAuthenticationConverter) {
        this.jwtDecoder = jwtDecoder;
        this.keycloakAuthenticationConverter = keycloakAuthenticationConverter;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    List<String> authorization = accessor.getNativeHeader("Authorization");
                    log.debug("Authorization: {}", authorization);
                    if (authorization == null || authorization.isEmpty()) {
                        return new ErrorMessage(
                                new IllegalStateException("Client not authorized to connect without a valid token"));
                    }
                    String accessToken = authorization.get(0).split(" ")[1];
                    Jwt jwt = jwtDecoder.decode(accessToken);
                    Authentication authentication = keycloakAuthenticationConverter.convert(jwt);
                    accessor.setUser(authentication);
                    log.info("{} successfully authenticated to websocket", authentication.getName());
                }
                return message;
            }
        });
    }
}
