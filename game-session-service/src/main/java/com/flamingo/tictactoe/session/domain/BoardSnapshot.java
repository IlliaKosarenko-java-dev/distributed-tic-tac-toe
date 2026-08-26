package com.flamingo.tictactoe.session.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A read-only view of the board as last reported by the engine.
 *
 * <p>This service does not decide legality — that is the engine's job, and asking twice would
 * create two places for the rules to drift. What it does need is enough board awareness to
 * *choose* a move: which cells are free, and whether a candidate cell completes a line. That
 * is why line detection appears here as well as in the engine; the two answer different
 * questions, one authoritative and one advisory.
 */
public final class BoardSnapshot {

    public static final int CELL_COUNT = 9;

    private static final int[][] LINES = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
            {0, 4, 8}, {2, 4, 6}
    };

    private final Mark[] cells;

    private BoardSnapshot(Mark[] cells) {
        this.cells = cells;
    }

    public static BoardSnapshot empty() {
        return new BoardSnapshot(new Mark[CELL_COUNT]);
    }

    public static BoardSnapshot of(List<Mark> cells) {
        if (cells == null || cells.size() != CELL_COUNT) {
            throw new IllegalArgumentException(
                    "A board needs exactly %d cells but got %s".formatted(
                            CELL_COUNT, cells == null ? "null" : cells.size()));
        }
        return new BoardSnapshot(cells.toArray(new Mark[0]));
    }

    public Optional<Mark> at(int position) {
        return Optional.ofNullable(cells[position]);
    }

    public boolean isFree(int position) {
        return cells[position] == null;
    }

    public List<Integer> freePositions() {
        List<Integer> free = new ArrayList<>(CELL_COUNT);
        for (int i = 0; i < CELL_COUNT; i++) {
            if (cells[i] == null) {
                free.add(i);
            }
        }
        return Collections.unmodifiableList(free);
    }

    public boolean isFull() {
        return freePositions().isEmpty();
    }

    /** A copy with one more mark, used to ask "what if I played here?". */
    public BoardSnapshot withMark(int position, Mark mark) {
        Mark[] updated = cells.clone();
        updated[position] = mark;
        return new BoardSnapshot(updated);
    }

    /** Whether the given player already holds three in a row. */
    public boolean hasLineFor(Mark mark) {
        for (int[] line : LINES) {
            if (cells[line[0]] == mark && cells[line[1]] == mark && cells[line[2]] == mark) {
                return true;
            }
        }
        return false;
    }

    public List<Mark> cells() {
        return Collections.unmodifiableList(Arrays.asList(cells.clone()));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BoardSnapshot board && Arrays.equals(cells, board.cells);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(cells);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CELL_COUNT; i++) {
            sb.append(cells[i] == null ? "." : cells[i].name());
        }
        return sb.toString();
    }
}
