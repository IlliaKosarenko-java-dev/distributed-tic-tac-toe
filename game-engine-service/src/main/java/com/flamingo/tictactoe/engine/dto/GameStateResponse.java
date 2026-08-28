package com.flamingo.tictactoe.engine.dto;

import com.flamingo.tictactoe.engine.repository.StoredGame;
import com.flamingo.tictactoe.engine.domain.Game;
import com.flamingo.tictactoe.engine.domain.Move;
import com.flamingo.tictactoe.engine.domain.Player;
import com.flamingo.tictactoe.engine.domain.GameStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * @param board       nine cells in reading order; a free cell is null
 * @param nextPlayer  only meaningful while the status is IN_PROGRESS
 * @param version     concurrency token to echo back in the next move's expectedVersion
 * @param winningLine the three cells that ended the game, or null
 * @param lastMove    the move just applied; null when the state was merely read
 */
public record GameStateResponse(
        UUID gameId,
        List<Player> board,
        Player nextPlayer,
        GameStatus status,
        int moveCount,
        long version,
        @Schema(description = "Cells forming the winning line") List<Integer> winningLine,
        AppliedMove lastMove) {

    public static GameStateResponse of(StoredGame stored) {
        return of(stored, null);
    }

    public static GameStateResponse of(StoredGame stored, Move lastMove) {
        Game game = stored.game();
        return new GameStateResponse(
                game.id(),
                game.board().cells(),
                game.nextPlayer(),
                game.status(),
                game.moveCount(),
                stored.version(),
                game.winningLine().map(line -> line.positions()).orElse(null),
                lastMove == null ? null : new AppliedMove(lastMove.player(), lastMove.position().index()));
    }

    public record AppliedMove(Player player, int position) {
    }
}
