package com.Color_craze.board.models;

import java.util.UUID;

import com.Color_craze.utils.enums.ColorStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Player extends Box{
    private UUID id;
    private int col, Row;
    private int score;

    public Player(UUID id, ColorStatus color) {
        super(color != null ? color : ColorStatus.PINK);
        this.id = id;
        ColorStatus effective = this.color; // set by Box constructor
        switch (effective) {
        case PINK -> {Row = 14;
            col = 30;}
        case YELLOW -> {Row = 14;
            col = 1;}
        case PURPLE -> {Row = 4;
            col = 10;}
        case GREEN -> {Row = 4;
            col = 22;}
        default -> {Row = -1;
            col = -1;}
    }}
}
