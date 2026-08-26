package com.flamingo.tictactoe.session.domain;

/** Builds board positions directly, so a test can state the position it cares about. */
public final class BoardFixtures {

    private BoardFixtures() {
    }

    /** @param spec nine characters of {@code X}, {@code O} or {@code .} — e.g. "XX.OO...." */
    public static BoardSnapshot board(String spec) {
        if (spec.length() != BoardSnapshot.CELL_COUNT) {
            throw new IllegalArgumentException("Board spec must be 9 characters, got: " + spec);
        }
        java.util.List<Mark> cells = new java.util.ArrayList<>(BoardSnapshot.CELL_COUNT);
        for (char c : spec.toCharArray()) {
            cells.add(switch (c) {
                case 'X' -> Mark.X;
                case 'O' -> Mark.O;
                case '.' -> null;
                default -> throw new IllegalArgumentException("Unexpected cell '" + c + "'");
            });
        }
        return BoardSnapshot.of(cells);
    }
}
