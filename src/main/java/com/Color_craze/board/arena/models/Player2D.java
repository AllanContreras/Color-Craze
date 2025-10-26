package com.Color_craze.board.arena.models;

import com.Color_craze.utils.enums.ColorStatus;

public class Player2D {
    public final String playerId;
    public double x, y; // position top-left
    public double vx, vy;
    public boolean onGround;
    public final ColorStatus color;
    public int score;
    // Cooldown to rate-limit scoring (+1) frequency
    public long lastAwardMs = 0L;
    public static final double WIDTH = 24;
    public static final double HEIGHT = 32;

    public Player2D(String playerId, double x, double y, ColorStatus color){
        this.playerId = playerId;
        this.x = x; this.y = y;
        this.color = color;
    }
}
