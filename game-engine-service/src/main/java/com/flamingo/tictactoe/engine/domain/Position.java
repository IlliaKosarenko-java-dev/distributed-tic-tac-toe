package com.flamingo.tictactoe.engine.domain;


import com.flamingo.tictactoe.engine.domain.exception.InvalidPositionException;

/**
 * A cell index on the board, numbered left to right, top to bottom:
 *
 * <pre>
 *   0 | 1 | 2
 *   3 | 4 | 5
 *   6 | 7 | 8
 * </pre>
 */
public record Position(int index) {

    public Position {
        if (index < 0 || index >= Board.CELL_COUNT) {
            throw new InvalidPositionException(index);
        }
    }

    public static Position of(int index) {
        return new Position(index);
    }
}
