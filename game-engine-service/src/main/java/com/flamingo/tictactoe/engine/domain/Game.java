package com.flamingo.tictactoe.engine.domain;

import java.util.UUID;

import com.flamingo.tictactoe.engine.domain.exception.MoveRejectedException;

import java.util.Objects;
import java.util.Optional;

/**
 * The game aggregate: the single place where tic-tac-toe rules live.
 *
 * <p>Immutable — {@link #applyMove(Move)} returns the next state rather than mutating this
 * one. That is what lets the persistence adapter treat a move as a compare-and-swap: load,
 * apply, store the result, and let the version check reject a concurrent writer.
 */
public final class Game {

    private final UUID id;
    private final Board board;
    private final Player nextPlayer;
    private final GameStatus status;
    private final int moveCount;
    private final WinningLine winningLine;

    private Game(UUID id, Board board, Player nextPlayer, GameStatus status,
                 int moveCount, WinningLine winningLine) {
        this.id = id;
        this.board = board;
        this.nextPlayer = nextPlayer;
        this.status = status;
        this.moveCount = moveCount;
        this.winningLine = winningLine;
    }

    public static Game newGame(UUID id, Player startingPlayer) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(startingPlayer, "startingPlayer");
        return new Game(id, Board.empty(), startingPlayer, GameStatus.IN_PROGRESS, 0, null);
    }

    /**
     * Restores a game from stored state. Does not re-derive the status: a persisted game is
     * trusted, and re-running rules on load would hide corruption rather than surface it.
     */
    public static Game restore(UUID id, Board board, Player nextPlayer, GameStatus status,
                               int moveCount, WinningLine winningLine) {
        return new Game(id, board, nextPlayer, status, moveCount, winningLine);
    }

    /**
     * @return the game state after the move
     * @throws MoveRejectedException if the move is illegal in the current state
     */
    public Game applyMove(Move move) {
        Objects.requireNonNull(move, "move");

        if (status.isTerminal()) {
            throw new MoveRejectedException(MoveRejectionReason.GAME_ALREADY_FINISHED,
                    "Game %s already finished with status %s".formatted(id, status));
        }
        if (move.player() != nextPlayer) {
            throw new MoveRejectedException(MoveRejectionReason.NOT_PLAYERS_TURN,
                    "It is %s's turn, not %s's".formatted(nextPlayer, move.player()));
        }
        if (board.isOccupied(move.position())) {
            throw new MoveRejectedException(MoveRejectionReason.CELL_OCCUPIED,
                    "Position %d is already taken by %s".formatted(
                            move.position().index(),
                            board.at(move.position()).orElseThrow()));
        }

        Board updated = board.mark(move.position(), move.player());
        WinningLine line = updated.winningLine().orElse(null);

        GameStatus nextStatus;
        if (line != null) {
            nextStatus = GameStatus.wonBy(move.player());
        } else if (updated.isFull()) {
            nextStatus = GameStatus.DRAW;
        } else {
            nextStatus = GameStatus.IN_PROGRESS;
        }

        return new Game(id, updated, move.player().opponent(), nextStatus, moveCount + 1, line);
    }

    public UUID id() {
        return id;
    }

    public Board board() {
        return board;
    }

    /** Only meaningful while {@link #status()} is IN_PROGRESS; keeps advancing afterwards. */
    public Player nextPlayer() {
        return nextPlayer;
    }

    public GameStatus status() {
        return status;
    }

    public int moveCount() {
        return moveCount;
    }

    public Optional<WinningLine> winningLine() {
        return Optional.ofNullable(winningLine);
    }

    @Override
    public String toString() {
        return "Game[id=%s, status=%s, moveCount=%d, next=%s]".formatted(id, status, moveCount, nextPlayer);
    }
}
