package com.flamingo.tictactoe.session.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The engine's game payload, exactly as it appears on the wire.
 *
 * <p>Ignoring unknown fields on purpose: the engine may add to its response without this service
 * needing to know or redeploy. That tolerance is what keeps two independently deployed services
 * genuinely independent.
 *
 * @param board nine cells in reading order, "X", "O", or null
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EngineGameStateResponse(
        String gameId,
        List<String> board,
        String nextPlayer,
        String status,
        int moveCount,
        long version,
        List<Integer> winningLine) {
}
