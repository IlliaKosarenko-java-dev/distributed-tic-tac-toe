package com.flamingo.tictactoe.engine.dto;

import com.flamingo.tictactoe.engine.domain.Player;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * @param gameId         optional; the session service supplies its own id so a session and its
 *                       game share one identifier. Generated when absent. No length or charset
 *                       constraints are needed — a value either parses as a UUID or the request
 *                       is rejected before it reaches the domain.
 * @param startingPlayer optional; defaults to X.
 */
public record CreateGameRequest(
        @Schema(description = "Caller-chosen id. Generated when omitted.",
                example = "3f1c8a52-6a1b-4c3e-9d2f-8b7a1c4e5f60")
        UUID gameId,

        @Schema(description = "Who moves first. Defaults to X.", example = "X")
        Player startingPlayer) {

    public Player startingPlayerOrDefault() {
        return startingPlayer == null ? Player.X : startingPlayer;
    }
}
