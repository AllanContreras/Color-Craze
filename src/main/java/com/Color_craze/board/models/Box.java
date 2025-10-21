package com.Color_craze.board.models;

import com.Color_craze.utils.enums.ColorStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Box {
    protected ColorStatus color;
}
