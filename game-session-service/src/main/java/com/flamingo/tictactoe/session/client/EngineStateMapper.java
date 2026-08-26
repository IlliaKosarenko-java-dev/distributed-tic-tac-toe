package com.flamingo.tictactoe.session.client;

import com.flamingo.tictactoe.session.client.dto.EngineGameStateResponse;
import com.flamingo.tictactoe.session.client.exception.EngineUnavailableException;
import com.flamingo.tictactoe.session.domain.BoardSnapshot;
import com.flamingo.tictactoe.session.domain.GameOutcome;
import com.flamingo.tictactoe.session.domain.Mark;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Turns the engine's payload into session-domain types.
 *
 * <p>The two services deliberately share no Java types, so the agreement between them is the
 * JSON — string for string. That agreement is checked here, once, and a mismatch is reported as
 * a clear failure rather than an enum-parsing stack trace from somewhere deep in a simulation.
 */
@Component
public class EngineStateMapper {

    public EngineGameState toDomain(EngineGameStateResponse response) {
        if (response == null) {
            throw new EngineUnavailableException("Engine returned an empty body");
        }
        return new EngineGameState(
                response.gameId(),
                toBoard(response.board()),
                toMark(response.nextPlayer(), "nextPlayer"),
                toOutcome(response.status()),
                response.moveCount(),
                response.version(),
                response.winningLine() == null ? List.of() : List.copyOf(response.winningLine()));
    }

    private BoardSnapshot toBoard(List<String> cells) {
        if (cells == null || cells.size() != BoardSnapshot.CELL_COUNT) {
            throw new EngineUnavailableException(
                    "Engine returned a board of %s cells".formatted(cells == null ? "null" : cells.size()));
        }
        return BoardSnapshot.of(cells.stream().map(cell -> toMarkOrNull(cell)).toList());
    }

    /** A free cell is null on the wire and stays null here. */
    private Mark toMarkOrNull(String value) {
        return value == null ? null : toMark(value, "board cell");
    }

    private Mark toMark(String value, String field) {
        try {
            return Mark.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException unknown) {
            throw new EngineUnavailableException(
                    "Engine reported %s='%s', which is not a known mark".formatted(field, value));
        }
    }

    private GameOutcome toOutcome(String value) {
        try {
            return GameOutcome.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException unknown) {
            throw new EngineUnavailableException(
                    "Engine reported status='%s', which is not a known outcome".formatted(value));
        }
    }
}
