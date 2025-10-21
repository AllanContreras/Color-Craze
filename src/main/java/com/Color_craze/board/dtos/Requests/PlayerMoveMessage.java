package com.Color_craze.board.dtos.Requests;

import com.Color_craze.utils.enums.PlayerMove;
import lombok.Data;

@Data
public class PlayerMoveMessage {
    private String playerId;
    private PlayerMove direction;
}
