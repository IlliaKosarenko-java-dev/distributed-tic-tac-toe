package com.flamingo.tictactoe.session.repository;

import com.flamingo.tictactoe.session.domain.GameOutcome;
import com.flamingo.tictactoe.session.domain.SessionStatus;
import com.flamingo.tictactoe.session.domain.StrategyType;

/**
 * Filters for listing sessions, newest first. A null field means "don't filter on this".
 *
 * <p>The reason this exists: with two different strategies playing each other, the interesting
 * question is whether one actually beats the other. That is only answerable if finished sessions
 * are queryable by outcome and by who was playing.
 */
public record SessionQuery(SessionStatus status, GameOutcome outcome,
                           StrategyType xStrategy, StrategyType oStrategy, int limit) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 200;

    public SessionQuery {
        limit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }

    public static SessionQuery recent(int limit) {
        return new SessionQuery(null, null, null, null, limit);
    }
}
