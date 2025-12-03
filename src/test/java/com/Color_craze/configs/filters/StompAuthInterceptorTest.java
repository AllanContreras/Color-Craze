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
package com.Color_craze.configs.filters;

import com.Color_craze.auth.services.JwtService;
import com.Color_craze.board.models.GameSession;
import com.Color_craze.board.repositories.GameRepository;
import com.Color_craze.utils.enums.ColorStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StompAuthInterceptor covering destination validation, membership check, and anti-flood.
 */
class StompAuthInterceptorTest {
    private JwtService jwtService;
    private GameRepository gameRepository;
    private StompAuthInterceptor interceptor;
    private MessageChannel dummyChannel;

    @BeforeEach
    void setup() {
        jwtService = mock(JwtService.class);
        gameRepository = mock(GameRepository.class);
        interceptor = new StompAuthInterceptor();
        // Inject mocks via reflection since fields are package-private/autowired
        try {
            var fJwt = StompAuthInterceptor.class.getDeclaredField("jwtService");
            fJwt.setAccessible(true);
            fJwt.set(interceptor, jwtService);
            var fRepo = StompAuthInterceptor.class.getDeclaredField("gameRepository");
            fRepo.setAccessible(true);
            fRepo.set(interceptor, gameRepository);
        } catch (Exception e) {
            fail("No se pudo inyectar dependencias mock: " + e.getMessage());
        }
        dummyChannel = Mockito.mock(MessageChannel.class);
    }

    private Message<byte[]> buildMessage(StompCommand cmd, String destination, String authHeader, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(cmd);
        if (destination != null) accessor.setDestination(destination);
        if (authHeader != null) accessor.addNativeHeader("Authorization", authHeader);
        if (sessionId != null) accessor.setSessionId(sessionId);
        return MessageBuilder.withPayload(new byte[0]).setHeaders(accessor).build();
    }

    @Test
    void subscribeInvalidDestinationThrows() {
        Message<byte[]> msg = buildMessage(StompCommand.SUBSCRIBE, "/topic/invalid/ABC", null, "sess1");
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(msg, dummyChannel));
    }

    @Test
    void subscribeWithJwtAndNonMemberThrows() {
        when(jwtService.extractUsername("tok123")).thenReturn("userX");
        GameSession gs = new GameSession();
        gs.setCode("ROOM1");
        gs.setStatus("WAITING");
        // Add a different player so userX is not member
        gs.getPlayers().add(new GameSession.PlayerEntry("other", "Other", ColorStatus.YELLOW));
        when(gameRepository.findByCode("ROOM1")).thenReturn(Optional.of(gs));

        Message<byte[]> msg = buildMessage(StompCommand.SUBSCRIBE, "/topic/board/ROOM1/state", "Bearer tok123", "sess2");
        assertThrows(IllegalStateException.class, () -> interceptor.preSend(msg, dummyChannel));
    }

    @Test
    void subscribeWithJwtAndMemberPasses() {
        when(jwtService.extractUsername("tok456")).thenReturn("playerA");
        GameSession gs = new GameSession();
        gs.setCode("ROOM2");
        gs.setStatus("WAITING");
        gs.getPlayers().add(new GameSession.PlayerEntry("playerA", "playerA", ColorStatus.GREEN));
        when(gameRepository.findByCode("ROOM2")).thenReturn(Optional.of(gs));

        Message<byte[]> msg = buildMessage(StompCommand.SUBSCRIBE, "/topic/board/ROOM2/state", "Bearer tok456", "sess3");
        Message<?> out = interceptor.preSend(msg, dummyChannel);
        assertNotNull(out, "Mensaje debe pasar cuando el usuario pertenece a la sala");
    }

    @Test
    void connectFloodFailsSecondAttemptWithinOneSecond() {
        Message<byte[]> first = buildMessage(StompCommand.CONNECT, null, null, "sessFlood");
        Message<?> ok = interceptor.preSend(first, dummyChannel);
        assertNotNull(ok);
        Message<byte[]> second = buildMessage(StompCommand.CONNECT, null, null, "sessFlood");
        assertThrows(IllegalStateException.class, () -> interceptor.preSend(second, dummyChannel));
    }
}
