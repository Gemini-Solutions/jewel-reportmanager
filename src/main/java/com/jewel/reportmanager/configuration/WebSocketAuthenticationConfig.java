package com.jewel.reportmanager.configuration;

import com.jewel.reportmanager.service.JwtHelperService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketAuthenticationConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private JwtHelperService jwtHelper;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) throws NullPointerException {
        registration.interceptors(new ChannelInterceptor() {


            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null) {
                    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                        List<String> authorization = accessor.getNativeHeader("Authorization");
                        if (authorization != null) {
                            String accessToken = authorization.get(0).split(" ")[1];
                            String username = jwtHelper.getUserNameFromJwtToken(accessToken);
                            GrantedAuthority authority = new SimpleGrantedAuthority(username);
                            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(username,
                                    accessToken, Arrays.asList(authority));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                            accessor.setUser(auth);
                        }
                    }
                }

                return message;
            }
        });
    }
}