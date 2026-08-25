package com.flamingo.tictactoe.engine.domain;

import java.util.List;

/**
 * The three cells that ended the game, so the UI can highlight them.
 */
public record WinningLine(Player player, List<Integer> positions) {

    public WinningLine {
        positions = List.copyOf(positions);
    }
}
