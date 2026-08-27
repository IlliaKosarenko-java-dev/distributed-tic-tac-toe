package com.flamingo.tictactoe.session.repository;

import com.flamingo.tictactoe.session.domain.Session;
import com.flamingo.tictactoe.session.service.exception.ConcurrentSessionUpdateException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SessionRepository {

    Optional<StoredSession> findById(String sessionId);

    StoredSession create(Session session);

    /**
     * Atomically moves a session from CREATED to RUNNING and records who owns the run.
     *
     * <p>This is the session-side compare-and-swap. Two callers racing to start the same
     * session — a double-clicked button, a retrying client, a second replica — cannot both
     * succeed, because the transition and the status check are one operation.
     *
     * @return the claimed session, or empty if it was not in CREATED, meaning someone else
     *         already owns it
     */
    Optional<StoredSession> claimForSimulation(String sessionId, String owner, Instant startedAt);

    /**
     * @throws ConcurrentSessionUpdateException if the store advanced since the caller's read
     */
    StoredSession save(StoredSession session);

    /** Sessions matching the query, newest first. */
    List<StoredSession> search(SessionQuery query);
}
