package com.flamingo.tictactoe.session.repository;

import com.flamingo.tictactoe.session.domain.Session;

/**
 * A session together with the version it was read at — the concurrency token belongs to the
 * store, not to the session itself.
 */
public record StoredSession(Session session, long version) {

    public StoredSession withSession(Session updated) {
        return new StoredSession(updated, version);
    }
}
