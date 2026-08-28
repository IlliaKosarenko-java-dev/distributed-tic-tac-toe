package com.flamingo.tictactoe.it;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;

/** Board helpers — the only thing in these tests that is genuinely ours rather than HTTP. */
final class Boards {

    private Boards() {
    }

    /** Reconstructs a board from a move history, to compare against what the services report. */
    static List<String> replay(JsonNode moves) {
        String[] board = new String[9];
        for (JsonNode move : moves) {
            int position = move.get("position").asInt();
            if (board[position] != null) {
                throw new AssertionError("Move history plays position " + position + " twice");
            }
            board[position] = move.get("player").asText();
        }
        return Arrays.asList(board);
    }

    /** A JSON board array as a list, with JSON nulls becoming Java nulls. */
    static List<String> of(JsonNode board) {
        return StreamSupport.stream(board.spliterator(), false)
                .map(cell -> cell.isNull() ? null : cell.asText())
                .toList();
    }
}
