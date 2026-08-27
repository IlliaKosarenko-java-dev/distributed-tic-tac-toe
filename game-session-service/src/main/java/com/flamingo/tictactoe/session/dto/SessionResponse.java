package com.flamingo.tictactoe.session.dto;

import com.flamingo.tictactoe.session.domain.GameOutcome;
import com.flamingo.tictactoe.session.domain.Mark;
import com.flamingo.tictactoe.session.domain.Session;
import com.flamingo.tictactoe.session.domain.SessionStatus;
import com.flamingo.tictactoe.session.domain.StrategyType;
import com.flamingo.tictactoe.session.repository.StoredSession;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * @param board  nine cells in reading order; a free cell is null
 * @param moves  the full history — bounded at nine, so there is no reason to paginate it
 */
public record SessionResponse(
        String sessionId,
        String gameId,
        SessionStatus status,
        StrategyType xStrategy,
        StrategyType oStrategy,
        long moveDelayMs,
        List<Mark> board,
        GameOutcome outcome,
        Mark nextPlayer,
        int moveCount,
        List<MoveDto> moves,
        long version,
        @Schema(description = "Instance driving the simulation, when one has claimed it")
        String simulationOwner,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String failureReason) {

    public static SessionResponse of(StoredSession stored) {
        Session session = stored.session();
        return new SessionResponse(
                session.sessionId(),
                session.gameId(),
                session.status(),
                session.xStrategy(),
                session.oStrategy(),
                session.moveDelayMs(),
                session.board().cells(),
                session.gameOutcome(),
                session.nextPlayer(),
                session.moveCount(),
                session.moves().stream().map(MoveDto::of).toList(),
                stored.version(),
                session.simulationOwner().orElse(null),
                session.createdAt(),
                session.startedAt().orElse(null),
                session.finishedAt().orElse(null),
                session.failureReason().orElse(null));
    }
}
