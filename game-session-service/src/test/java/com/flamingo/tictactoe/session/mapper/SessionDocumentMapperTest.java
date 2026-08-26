package com.flamingo.tictactoe.session.mapper;

import com.flamingo.tictactoe.session.domain.GameOutcome;
import com.flamingo.tictactoe.session.domain.Mark;
import com.flamingo.tictactoe.session.domain.MoveRecord;
import com.flamingo.tictactoe.session.domain.Session;
import com.flamingo.tictactoe.session.domain.SessionStatus;
import com.flamingo.tictactoe.session.domain.StrategyType;
import com.flamingo.tictactoe.session.repository.StoredSession;
import com.flamingo.tictactoe.session.repository.mongo.SessionDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.flamingo.tictactoe.session.domain.BoardFixtures.board;
import static org.assertj.core.api.Assertions.assertThat;

/** The mapping in isolation — no database, no Spring context. */
class SessionDocumentMapperTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final SessionDocumentMapper mapper = new SessionDocumentMapper();

    private static Session created() {
        return Session.create("s1", "s1", StrategyType.RULE_BASED, StrategyType.RANDOM, 250, NOW);
    }

    @Test
    void carriesAFreshSessionOntoTheDocument() {
        SessionDocument document = mapper.toDocument(created(), null);

        assertThat(document.id()).isEqualTo("s1");
        assertThat(document.gameId()).isEqualTo("s1");
        assertThat(document.status()).isEqualTo(SessionStatus.CREATED);
        assertThat(document.xStrategy()).isEqualTo(StrategyType.RULE_BASED);
        assertThat(document.oStrategy()).isEqualTo(StrategyType.RANDOM);
        assertThat(document.moveDelayMs()).isEqualTo(250);
        assertThat(document.board()).hasSize(9).containsOnlyNulls();
        assertThat(document.moves()).isEmpty();
        assertThat(document.version()).isNull();
        assertThat(document.simulationOwner()).isNull();
    }

    @Test
    void roundTripsAnEmptySessionUnchanged() {
        Session restored = mapper.toDomain(mapper.toDocument(created(), 0L));

        assertThat(restored.sessionId()).isEqualTo("s1");
        assertThat(restored.status()).isEqualTo(SessionStatus.CREATED);
        assertThat(restored.moves()).isEmpty();
        assertThat(restored.board().freePositions()).hasSize(9);
        assertThat(restored.nextPlayer()).isEqualTo(Mark.X);
    }

    @Test
    void roundTripsTheMoveHistoryInOrder() {
        Session played = created().claimedBy("instance-a", NOW)
                .withMove(new MoveRecord(1, Mark.X, 4, NOW), board("....X...."), GameOutcome.IN_PROGRESS)
                .withMove(new MoveRecord(2, Mark.O, 0, NOW), board("O...X...."), GameOutcome.IN_PROGRESS);

        Session restored = mapper.toDomain(mapper.toDocument(played, 2L));

        assertThat(restored.moves())
                .extracting(MoveRecord::seq, MoveRecord::player, MoveRecord::position)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, Mark.X, 4),
                        org.assertj.core.groups.Tuple.tuple(2, Mark.O, 0));
        assertThat(restored.nextPlayer())
                .as("turn order is rebuilt from history, so history order matters")
                .isEqualTo(Mark.X);
    }

    @Test
    void roundTripsAFinishedSessionWithItsOutcome() {
        Session finished = created().claimedBy("instance-a", NOW)
                .withMove(new MoveRecord(1, Mark.X, 2, NOW), board("XXXOO...."), GameOutcome.X_WON)
                .finished(NOW);

        Session restored = mapper.toDomain(mapper.toDocument(finished, 3L));

        assertThat(restored.status()).isEqualTo(SessionStatus.FINISHED);
        assertThat(restored.gameOutcome()).isEqualTo(GameOutcome.X_WON);
        assertThat(restored.finishedAt()).contains(NOW);
        assertThat(restored.simulationOwner()).contains("instance-a");
    }

    @Test
    void roundTripsAFailedSessionWithItsReason() {
        Session failed = created().claimedBy("instance-a", NOW).failed("engine unreachable", NOW);

        Session restored = mapper.toDomain(mapper.toDocument(failed, 2L));

        assertThat(restored.status()).isEqualTo(SessionStatus.FAILED);
        assertThat(restored.failureReason()).contains("engine unreachable");
    }

    @Test
    void keepsFreeCellsFreeAcrossTheRoundTrip() {
        Session played = created().withMove(
                new MoveRecord(1, Mark.X, 4, NOW), board("....X...."), GameOutcome.IN_PROGRESS);

        Session restored = mapper.toDomain(mapper.toDocument(played, 1L));

        assertThat(restored.board().at(4)).contains(Mark.X);
        assertThat(restored.board().at(0))
                .as("an empty cell must come back empty, not as a stray value")
                .isEmpty();
        assertThat(restored.board().freePositions()).hasSize(8);
    }

    @Test
    void treatsAMissingVersionAsZero() {
        StoredSession stored = mapper.toStoredSession(mapper.toDocument(created(), null));

        assertThat(stored.version()).isZero();
    }

    @Test
    void toleratesADocumentWithNoMoveArrayAtAll() {
        SessionDocument sparse = new SessionDocument(
                "s1", "s1", SessionStatus.CREATED, StrategyType.RANDOM, StrategyType.RANDOM,
                0, board(".........").cells(), GameOutcome.IN_PROGRESS,
                null, null, 0L, NOW, null, null, null);

        assertThat(mapper.toDomain(sparse).moves()).isEmpty();
    }
}
