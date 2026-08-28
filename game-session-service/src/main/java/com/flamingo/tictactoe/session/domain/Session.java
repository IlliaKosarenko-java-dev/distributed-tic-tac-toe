package com.flamingo.tictactoe.session.domain;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.With;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A single automated game: who is playing, how fast, and what has happened so far.
 *
 * <p>Immutable, like the engine's aggregate — each transition returns the next state, so the
 * repository can treat a write as a compare-and-swap rather than a blind overwrite.
 *
 * <p>The generated withers are private on purpose. They exist to let the transition methods
 * below build the next state without a fourteen-argument copy helper; making them public would
 * let any caller set any field, which is precisely the freedom this type is meant to withhold.
 */
@Getter
@Accessors(fluent = true)
@With(AccessLevel.PRIVATE)
public final class Session {

    private final UUID sessionId;
    private final UUID gameId;
    private final SessionStatus status;
    private final StrategyType xStrategy;
    private final StrategyType oStrategy;
    private final long moveDelayMs;
    private final BoardSnapshot board;
    private final GameOutcome gameOutcome;
    private final List<MoveRecord> moves;
    private final String simulationOwner;
    private final Instant createdAt;
    private final Instant startedAt;
    private final Instant finishedAt;
    private final String failureReason;

    /**
     * Hand-written rather than {@code @AllArgsConstructor}: the defensive copy of the move list
     * is what makes instances genuinely immutable, and a generated constructor would assign the
     * caller's list straight through.
     */
    private Session(UUID sessionId, UUID gameId, SessionStatus status,
                    StrategyType xStrategy, StrategyType oStrategy, long moveDelayMs,
                    BoardSnapshot board, GameOutcome gameOutcome, List<MoveRecord> moves,
                    String simulationOwner, Instant createdAt, Instant startedAt,
                    Instant finishedAt, String failureReason) {
        this.sessionId = sessionId;
        this.gameId = gameId;
        this.status = status;
        this.xStrategy = xStrategy;
        this.oStrategy = oStrategy;
        this.moveDelayMs = moveDelayMs;
        this.board = board;
        this.gameOutcome = gameOutcome;
        this.moves = List.copyOf(moves);
        this.simulationOwner = simulationOwner;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.failureReason = failureReason;
    }

    public static Session create(UUID sessionId, UUID gameId, StrategyType xStrategy,
                                 StrategyType oStrategy, long moveDelayMs, Instant createdAt) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(gameId, "gameId");
        return new Session(sessionId, gameId, SessionStatus.CREATED, xStrategy, oStrategy,
                moveDelayMs, BoardSnapshot.empty(), GameOutcome.IN_PROGRESS, List.of(),
                null, createdAt, null, null, null);
    }

    /** Rebuilds a session from stored state without re-deriving anything. */
    public static Session restore(UUID sessionId, UUID gameId, SessionStatus status,
                                  StrategyType xStrategy, StrategyType oStrategy, long moveDelayMs,
                                  BoardSnapshot board, GameOutcome gameOutcome, List<MoveRecord> moves,
                                  String simulationOwner, Instant createdAt, Instant startedAt,
                                  Instant finishedAt, String failureReason) {
        return new Session(sessionId, gameId, status, xStrategy, oStrategy, moveDelayMs, board,
                gameOutcome, moves, simulationOwner, createdAt, startedAt, finishedAt, failureReason);
    }

    /** Whose turn it is, derived from the move count rather than stored separately. */
    public Mark nextPlayer() {
        return moves.size() % 2 == 0 ? Mark.X : Mark.O;
    }

    public StrategyType strategyFor(Mark player) {
        return player == Mark.X ? xStrategy : oStrategy;
    }

    public int moveCount() {
        return moves.size();
    }

    /** Marks this session as owned by a runner. Only meaningful from CREATED. */
    public Session claimedBy(String owner, Instant startedAt) {
        return withStatus(SessionStatus.RUNNING)
                .withSimulationOwner(owner)
                .withStartedAt(startedAt);
    }

    /** Appends a move and refreshes the cached board and outcome from what the engine returned. */
    public Session withMove(MoveRecord move, BoardSnapshot updatedBoard, GameOutcome outcome) {
        List<MoveRecord> appended = new ArrayList<>(moves);
        appended.add(move);
        return withMoves(appended)
                .withBoard(updatedBoard)
                .withGameOutcome(outcome);
    }

    public Session finished(Instant finishedAt) {
        return withStatus(SessionStatus.FINISHED).withFinishedAt(finishedAt);
    }

    public Session failed(String reason, Instant finishedAt) {
        return withStatus(SessionStatus.FAILED)
                .withFailureReason(reason)
                .withFinishedAt(finishedAt);
    }

    /** Already immutable — {@link List#copyOf} in the constructor guarantees it. */
    public List<MoveRecord> moves() {
        return moves;
    }

    public Optional<String> simulationOwner() {
        return Optional.ofNullable(simulationOwner);
    }

    public Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt);
    }

    public Optional<Instant> finishedAt() {
        return Optional.ofNullable(finishedAt);
    }

    public Optional<String> failureReason() {
        return Optional.ofNullable(failureReason);
    }

    @Override
    public String toString() {
        return "Session[id=%s, status=%s, moves=%d, outcome=%s]"
                .formatted(sessionId, status, moves.size(), gameOutcome);
    }
}
