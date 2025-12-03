package com.Color_craze.board.services;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class MoveRateLimiterTest {

    @Test
    void allowsUpToLimitThenBlocks(){
        MoveRateLimiter limiter = new MoveRateLimiter();
        String game = "g1"; String player = "p1";
        // 20 allowed
        for(int i=0;i<20;i++) assertTrue(limiter.allow(game, player));
        // next should be blocked
        assertFalse(limiter.allow(game, player));
    }
}
