package com.flamingo.tictactoe.session.service;

import java.util.UUID;
import com.flamingo.tictactoe.session.domain.GameOutcome;
import com.flamingo.tictactoe.session.domain.Mark;
import com.flamingo.tictactoe.session.domain.SessionStatus;

import java.util.List;

/**
 * Something worth telling a watcher about.
 *
 * <p>Sealed so the set of things that can be observed is fixed and visible in one place — the
 * UI's contract is this file. The names double as SSE event names.
 */
public sealed interface SessionEvent {

    UUID sessionId();

    /** The SSE event name a browser listens for. */
    String name();

    record Snapshot(UUID sessionId, SessionStatus status, GameOutcome outcome,
                    List<Mark> board, int moveCount) implements SessionEvent {
        @Override
        public String name() {
            return "snapshot";
        }
    }

    record MoveMade(UUID sessionId, int seq, Mark player, int position,
                    List<Mark> board, GameOutcome outcome, long version) implements SessionEvent {
        @Override
        public String name() {
            return "move";
        }
    }

    record StatusChanged(UUID sessionId, SessionStatus status) implements SessionEvent {
        @Override
        public String name() {
            return "status";
        }
    }

    record Finished(UUID sessionId, GameOutcome outcome, int moveCount,
                    List<Integer> winningLine) implements SessionEvent {
        @Override
        public String name() {
            return "finished";
        }
    }

    /** A simulation that could not complete, with the engine's code when there was one. */
    record Failed(UUID sessionId, String code, String message) implements SessionEvent {
        @Override
        public String name() {
            return "error";
        }
    }
}
