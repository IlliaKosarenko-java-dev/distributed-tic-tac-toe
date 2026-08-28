package com.flamingo.tictactoe.session.mapper;

import com.flamingo.tictactoe.session.domain.BoardSnapshot;
import com.flamingo.tictactoe.session.domain.MoveRecord;
import com.flamingo.tictactoe.session.domain.Session;
import com.flamingo.tictactoe.session.repository.StoredSession;
import com.flamingo.tictactoe.session.repository.mongo.SessionDocument;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Translates between the {@link Session} aggregate and its stored form, keeping that knowledge
 * out of both the document and the repository.
 */
@Component
public class SessionDocumentMapper {

    /**
     * @param version the version the session was read at, or null for a document being inserted
     */
    public SessionDocument toDocument(Session session, Long version) {
        List<SessionDocument.MoveEntry> moves = session.moves().stream()
                .map(move -> new SessionDocument.MoveEntry(
                        move.seq(), move.player(), move.position(), move.at()))
                .toList();

        return new SessionDocument(
                session.sessionId().toString(),
                session.gameId().toString(),
                session.status(),
                session.xStrategy(),
                session.oStrategy(),
                session.moveDelayMs(),
                session.board().cells(),
                session.gameOutcome(),
                moves,
                session.simulationOwner().orElse(null),
                version,
                session.createdAt(),
                session.startedAt().orElse(null),
                session.finishedAt().orElse(null),
                session.failureReason().orElse(null));
    }

    public Session toDomain(SessionDocument document) {
        List<MoveRecord> moves = document.moves() == null
                ? List.of()
                : document.moves().stream()
                        .map(entry -> new MoveRecord(
                                entry.seq(), entry.player(), entry.position(), entry.at()))
                        .toList();

        return Session.restore(
                UUID.fromString(document.id()),
                UUID.fromString(document.gameId()),
                document.status(),
                document.xStrategy(),
                document.oStrategy(),
                document.moveDelayMs(),
                BoardSnapshot.of(document.board()),
                document.gameOutcome(),
                moves,
                document.simulationOwner(),
                document.createdAt(),
                document.startedAt(),
                document.finishedAt(),
                document.failureReason());
    }

    /** A version of null means the document has not been through an insert yet. */
    public StoredSession toStoredSession(SessionDocument document) {
        return new StoredSession(toDomain(document),
                document.version() == null ? 0L : document.version());
    }
}
