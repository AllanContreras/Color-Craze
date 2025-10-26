package com.Color_craze.board.arena.models;

public record Platform2D(double x, double y, double width, double height, int cells) {
    public boolean intersects(double rx, double ry, double rwidth, double rheight){
        return rx < x + width && rx + rwidth > x && ry < y + height && ry + rheight > y;
    }
}
