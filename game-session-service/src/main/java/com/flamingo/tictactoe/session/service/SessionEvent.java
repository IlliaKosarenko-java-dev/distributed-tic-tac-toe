package com.flamingo.tictactoe.session.service;

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

    String sessionId();

    /** The SSE event name a browser listens for. */
    String name();

    record Snapshot(String sessionId, SessionStatus status, GameOutcome outcome,
                    List<Mark> board, int moveCount) implements SessionEvent {
        @Override
        public String name() {
            return "snapshot";
        }
    }

    record MoveMade(String sessionId, int seq, Mark player, int position,
                    List<Mark> board, GameOutcome outcome, long version) implements SessionEvent {
        @Override
        public String name() {
            return "move";
        }
    }

    record StatusChanged(String sessionId, SessionStatus status) implements SessionEvent {
        @Override
        public String name() {
            return "status";
        }
    }

    record Finished(String sessionId, GameOutcome outcome, int moveCount,
                    List<Integer> winningLine) implements SessionEvent {
        @Override
        public String name() {
            return "finished";
        }
    }

    /** A simulation that could not complete, with the engine's code when there was one. */
    record Failed(String sessionId, String code, String message) implements SessionEvent {
        @Override
        public String name() {
            return "error";
        }
    }
}
