package com.flamingo.tictactoe.session.client;

import java.util.UUID;
import com.flamingo.tictactoe.session.domain.BoardSnapshot;
import com.flamingo.tictactoe.session.domain.GameOutcome;
import com.flamingo.tictactoe.session.domain.Mark;

import java.util.List;

/**
 * What the engine reported, expressed in this service's own vocabulary.
 *
 * <p>The translation stops the engine's wire format from spreading through the session service:
 * everything past this point deals in {@link Mark} and {@link GameOutcome}, so a change to the
 * engine's JSON is absorbed in one mapper rather than everywhere a response is read.
 *
 * @param nextPlayer whose turn it is, or null once the game has finished
 * @param version echo back on the next move to detect a game that moved on underneath us
 */
public record EngineGameState(
        UUID gameId,
        BoardSnapshot board,
        Mark nextPlayer,
        GameOutcome outcome,
        int moveCount,
        long version,
        List<Integer> winningLine) {
}
