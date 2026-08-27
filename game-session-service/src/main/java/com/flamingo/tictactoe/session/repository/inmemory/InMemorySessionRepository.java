package com.flamingo.tictactoe.session.repository.inmemory;

import com.flamingo.tictactoe.session.domain.Session;
import com.flamingo.tictactoe.session.domain.SessionStatus;
import com.flamingo.tictactoe.session.repository.SessionQuery;
import com.flamingo.tictactoe.session.repository.SessionRepository;
import com.flamingo.tictactoe.session.repository.StoredSession;
import com.flamingo.tictactoe.session.service.exception.ConcurrentSessionUpdateException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps sessions in a map so the service runs with no infrastructure.
 *
 * <p>{@code compute} gives the same all-or-nothing behaviour that MongoDB's single-document
 * updates provide — within this JVM. Cluster-wide, only the MongoDB adapter can make that
 * promise, which is precisely why it exists.
 */
@Repository
@Profile("in-memory")
public class InMemorySessionRepository implements SessionRepository {

    private final Map<String, StoredSession> sessions = new ConcurrentHashMap<>();

    @Override
    public Optional<StoredSession> findById(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public StoredSession create(Session session) {
        StoredSession fresh = new StoredSession(session, 0L);
        StoredSession existing = sessions.putIfAbsent(session.sessionId(), fresh);
        if (existing != null) {
            throw new IllegalStateException("Session %s already exists".formatted(session.sessionId()));
        }
        return fresh;
    }

    @Override
    public Optional<StoredSession> claimForSimulation(String sessionId, String owner, Instant startedAt) {
        // Only the caller whose mapping function observes CREATED records a result, so the
        // status check and the transition cannot be split by another thread.
        StoredSession[] claimed = new StoredSession[1];
        sessions.computeIfPresent(sessionId, (id, current) -> {
            if (current.session().status() != SessionStatus.CREATED) {
                return current;
            }
            claimed[0] = new StoredSession(
                    current.session().claimedBy(owner, startedAt), current.version() + 1);
            return claimed[0];
        });
        return Optional.ofNullable(claimed[0]);
    }

    @Override
    public StoredSession save(StoredSession session) {
        String sessionId = session.session().sessionId();

        StoredSession[] written = new StoredSession[1];
        sessions.computeIfPresent(sessionId, (id, current) -> {
            if (current.version() != session.version()) {
                return current;
            }
            written[0] = new StoredSession(session.session(), current.version() + 1);
            return written[0];
        });

        if (written[0] == null) {
            throw new ConcurrentSessionUpdateException(sessionId, session.version());
        }
        return written[0];
    }

    @Override
    public List<StoredSession> search(SessionQuery query) {
        return sessions.values().stream()
                .filter(stored -> matches(stored, query))
                .sorted(Comparator.comparing(
                        (StoredSession stored) -> stored.session().createdAt()).reversed())
                .limit(query.limit())
                .toList();
    }

    private static boolean matches(StoredSession stored, SessionQuery query) {
        var session = stored.session();
        return (query.status() == null || session.status() == query.status())
                && (query.outcome() == null || session.gameOutcome() == query.outcome())
                && (query.xStrategy() == null || session.xStrategy() == query.xStrategy())
                && (query.oStrategy() == null || session.oStrategy() == query.oStrategy());
    }
}
