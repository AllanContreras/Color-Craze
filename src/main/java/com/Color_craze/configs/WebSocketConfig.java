package com.Color_craze.configs;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired(required = false)
    private com.Color_craze.configs.filters.StompAuthInterceptor stompAuthInterceptor;

    @Value("${colorcraze.stomp.relay.enabled:false}")
    private boolean relayEnabled;
    @Value("${colorcraze.stomp.relay.host:localhost}")
    private String relayHost;
    @Value("${colorcraze.stomp.relay.port:61613}")
    private int relayPort;
    @Value("${colorcraze.stomp.relay.clientLogin:guest}")
    private String relayClientLogin;
    @Value("${colorcraze.stomp.relay.clientPasscode:guest}")
    private String relayClientPasscode;
    @Value("${colorcraze.stomp.relay.systemLogin:guest}")
    private String relaySystemLogin;
    @Value("${colorcraze.stomp.relay.systemPasscode:guest}")
    private String relaySystemPasscode;
    @Value("${colorcraze.stomp.relay.virtualHost:/}")
    private String relayVirtualHost;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/color-craze/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        if (relayEnabled) {
            registry.enableStompBrokerRelay("/topic")
                .setRelayHost(relayHost)
                .setRelayPort(relayPort)
                .setClientLogin(relayClientLogin)
                .setClientPasscode(relayClientPasscode)
                .setSystemLogin(relaySystemLogin)
                .setSystemPasscode(relaySystemPasscode)
                .setVirtualHost(relayVirtualHost);
        } else {
            registry.enableSimpleBroker("/topic");
        }
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        if (stompAuthInterceptor != null) {
            registration.interceptors(stompAuthInterceptor);
        }
    }
}
