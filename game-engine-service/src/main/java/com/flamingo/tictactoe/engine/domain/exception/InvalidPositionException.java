package com.flamingo.tictactoe.engine.domain.exception;


import com.flamingo.tictactoe.engine.domain.Board;

/**
 * A position outside the board was requested. This is a malformed request rather than a
 * rejected move, which is why it is modelled separately from {@link MoveRejectedException}:
 * the web layer maps it to 400, not 409.
 */
public class InvalidPositionException extends RuntimeException {

    private final int index;

    public InvalidPositionException(int index) {
        super("Position %d is outside the board (expected 0..%d)".formatted(index, Board.CELL_COUNT - 1));
        this.index = index;
    }

    public int index() {
        return index;
    }
}
