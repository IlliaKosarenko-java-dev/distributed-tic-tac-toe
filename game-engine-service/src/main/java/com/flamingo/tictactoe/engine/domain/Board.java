package com.flamingo.tictactoe.engine.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable 3x3 board. Every mutation returns a new instance, which keeps the game
 * aggregate free of aliasing bugs and makes the whole type trivially thread-safe.
 */
public final class Board {

    public static final int CELL_COUNT = 9;

    private static final int[][] LINES = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8},   // rows
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8},   // columns
            {0, 4, 8}, {2, 4, 6}               // diagonals
    };

    /** Index -> occupying player, or null for a free cell. */
    private final Player[] cells;

    private Board(Player[] cells) {
        this.cells = cells;
    }

    public static Board empty() {
        return new Board(new Player[CELL_COUNT]);
    }

    /**
     * Builds a board from a full snapshot, using null for free cells. Used by the
     * persistence adapter when rehydrating, and by tests to set up a position directly.
     */
    public static Board of(Player... cells) {
        if (cells.length != CELL_COUNT) {
            throw new IllegalArgumentException(
                    "A board needs exactly %d cells but got %d".formatted(CELL_COUNT, cells.length));
        }
        return new Board(cells.clone());
    }

    public Optional<Player> at(Position position) {
        return Optional.ofNullable(cells[position.index()]);
    }

    public boolean isOccupied(Position position) {
        return cells[position.index()] != null;
    }

    /**
     * @throws IllegalStateException if the cell is taken — callers are expected to have
     *         validated occupancy already, so reaching this is a programming error rather
     *         than a rejected move.
     */
    public Board mark(Position position, Player player) {
        Objects.requireNonNull(player, "player");
        if (isOccupied(position)) {
            throw new IllegalStateException("Cell %d is already occupied".formatted(position.index()));
        }
        Player[] updated = cells.clone();
        updated[position.index()] = player;
        return new Board(updated);
    }

    public List<Position> freePositions() {
        List<Position> free = new ArrayList<>(CELL_COUNT);
        for (int i = 0; i < CELL_COUNT; i++) {
            if (cells[i] == null) {
                free.add(Position.of(i));
            }
        }
        return Collections.unmodifiableList(free);
    }

    public boolean isFull() {
        for (Player cell : cells) {
            if (cell == null) {
                return false;
            }
        }
        return true;
    }

    public int markCount() {
        return CELL_COUNT - freePositions().size();
    }

    /**
     * @return the completed line, or empty if nobody has three in a row. At most one line
     *         can be complete in a legally played game.
     */
    public Optional<WinningLine> winningLine() {
        for (int[] line : LINES) {
            Player first = cells[line[0]];
            if (first != null && first == cells[line[1]] && first == cells[line[2]]) {
                return Optional.of(new WinningLine(first, List.of(line[0], line[1], line[2])));
            }
        }
        return Optional.empty();
    }

    /** Snapshot of every cell, nulls included, for mapping to DTOs and documents. */
    public List<Player> cells() {
        return Collections.unmodifiableList(Arrays.asList(cells.clone()));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Board board && Arrays.equals(cells, board.cells);
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
            sb.append((i % 3 == 2) ? "\n" : " ");
        }
        return sb.toString();
    }
}
