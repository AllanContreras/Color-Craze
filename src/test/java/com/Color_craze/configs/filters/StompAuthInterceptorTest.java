package com.Color_craze.configs.filters;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.*;

class StompAuthInterceptorTest {

    @Test
    void rateLimitDropsExcessSendMessages() {
        StompAuthInterceptor interceptor = new StompAuthInterceptor();

        String sessionId = "sess-123";
        int allowed = 0;
        int dropped = 0;

        for (int i = 0; i < 25; i++) {
            StompHeaderAccessor sha = StompHeaderAccessor.create(StompCommand.SEND);
            sha.setSessionId(sessionId);
            sha.setDestination("/app/test");
            Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], sha.getMessageHeaders());
            Message<?> out = interceptor.preSend(msg, null);
            if (out == null) dropped++; else allowed++;
        }

        assertTrue(allowed >= 20, "At least first 20 should be allowed");
        assertTrue(dropped >= 1, "Excess messages should be dropped");
    }
}
