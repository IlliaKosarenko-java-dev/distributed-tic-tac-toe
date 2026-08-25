package com.flamingo.tictactoe.engine.domain;

/**
 * Test helpers for building board positions directly, so a test can state the position it
 * cares about instead of a move sequence that happens to produce it.
 */
final class BoardFixtures {

    private BoardFixtures() {
    }

    /**
     * @param spec nine characters of {@code X}, {@code O} or {@code .} (free), read left to
     *             right, top to bottom — e.g. {@code "XX.OO...."}
     */
    static Board board(String spec) {
        if (spec.length() != Board.CELL_COUNT) {
            throw new IllegalArgumentException("Board spec must be 9 characters, got: " + spec);
        }
        Player[] cells = new Player[Board.CELL_COUNT];
        for (int i = 0; i < Board.CELL_COUNT; i++) {
            cells[i] = switch (spec.charAt(i)) {
                case 'X' -> Player.X;
                case 'O' -> Player.O;
                case '.' -> null;
                default -> throw new IllegalArgumentException("Unexpected cell '" + spec.charAt(i) + "'");
            };
        }
        return Board.of(cells);
    }

    /** Builds a board holding only the given player's marks on the given cells. */
    static Board boardWith(Player player, int... positions) {
        Player[] cells = new Player[Board.CELL_COUNT];
        for (int position : positions) {
            cells[position] = player;
        }
        return Board.of(cells);
    }
}
