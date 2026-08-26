package com.flamingo.tictactoe.session.repository.mongo;

import com.flamingo.tictactoe.session.domain.GameOutcome;
import com.flamingo.tictactoe.session.domain.Mark;
import com.flamingo.tictactoe.session.domain.SessionStatus;
import com.flamingo.tictactoe.session.domain.StrategyType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * The stored shape of a session, move history included.
 *
 * <p>The history is an embedded array rather than its own collection: it is bounded at nine
 * entries, always read whole, and never queried element by element — so a session and its full
 * log come back in one round trip.
 */
@Document(collection = "sessions")
@CompoundIndex(name = "status_finishedAt", def = "{'status': 1, 'finishedAt': -1}")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SessionDocument {

    @Id
    private String id;

    private String gameId;

    @Indexed
    private SessionStatus status;

    private StrategyType xStrategy;

    private StrategyType oStrategy;

    private long moveDelayMs;

    /** Nine cells in reading order; null means free. */
    private List<Mark> board;

    @Indexed
    private GameOutcome gameOutcome;

    private List<MoveEntry> moves;

    private String simulationOwner;

    @Version
    private Long version;

    private Instant createdAt;

    private Instant startedAt;

    private Instant finishedAt;

    private String failureReason;

    @Getter
    @Accessors(fluent = true)
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    public static class MoveEntry {

        private int seq;
        private Mark player;
        private int position;
        private Instant at;
    }
}
