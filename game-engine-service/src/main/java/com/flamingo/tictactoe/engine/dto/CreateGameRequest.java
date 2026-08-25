package com.flamingo.tictactoe.engine.dto;

import com.flamingo.tictactoe.engine.domain.Player;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param gameId         optional; the session service supplies its own id so a session and its
 *                       game share one identifier. Generated when absent. Constrained because
 *                       the value ends up in a URL path and as a document id — an unbounded or
 *                       exotic string has no legitimate use here.
 * @param startingPlayer optional; defaults to X.
 */
public record CreateGameRequest(
        @Size(max = 64, message = "must be at most 64 characters")
        @Pattern(regexp = "[A-Za-z0-9_-]*",
                message = "may contain only letters, digits, hyphens and underscores")
        @Schema(description = "Caller-chosen id. Generated when omitted.", example = "session-42")
        String gameId,

        @Schema(description = "Who moves first. Defaults to X.", example = "X")
        Player startingPlayer) {

    public Player startingPlayerOrDefault() {
        return startingPlayer == null ? Player.X : startingPlayer;
    }
}
