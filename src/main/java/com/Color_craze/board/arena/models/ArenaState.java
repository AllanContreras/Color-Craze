package com.Color_craze.board.arena.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.Color_craze.utils.enums.ColorStatus;

public class ArenaState {
    public final double width;
    public final double height;
    public final List<Platform2D> platforms = new ArrayList<>();
    public final Map<String, Player2D> players = new HashMap<>();
    // Paint per platform: array of color names per cell, null means unpainted
    public final Map<Integer, String[]> paint = new HashMap<>();
    // Per-player credit memory: which cells this player has been credited for
    // Key is a composed long: high 32 bits = platform index, low 32 bits = cell index
    public final Map<String, java.util.Set<Long>> creditedByPlayer = new HashMap<>();

    public ArenaState(double width, double height){
        this.width = width; this.height = height;
    }

    public void ensurePaintArray(int platformIndex, int cells){
        paint.computeIfAbsent(platformIndex, k -> new String[cells]);
    }

    public boolean creditPaint(int platformIndex, int cellIndex, ColorStatus color){
        String[] arr = paint.get(platformIndex);
        if (arr == null || cellIndex < 0 || cellIndex >= arr.length) return false;
        String current = arr[cellIndex];
        String newCol = color.name();
        if (newCol.equals(current)) return false;
        arr[cellIndex] = newCol;
        return true;
    }

    public static long cellKey(int platformIndex, int cellIndex){
        return (((long)platformIndex) << 32) | (cellIndex & 0xffffffffL);
    }
}
