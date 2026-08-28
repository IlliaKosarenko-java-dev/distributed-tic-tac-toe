package com.flamingo.tictactoe.session.service;

import java.util.UUID;
import com.flamingo.tictactoe.session.config.InstanceIdentity;
import com.flamingo.tictactoe.session.domain.GameOutcome;
import com.flamingo.tictactoe.session.domain.Mark;
import com.flamingo.tictactoe.session.domain.SessionStatus;
import com.flamingo.tictactoe.session.domain.StrategyType;
import com.flamingo.tictactoe.session.repository.StoredSession;
import com.flamingo.tictactoe.session.repository.inmemory.InMemorySessionRepository;
import com.flamingo.tictactoe.session.service.exception.SessionNotFoundException;
import com.flamingo.tictactoe.session.service.exception.SimulationAlreadyStartedException;
import com.flamingo.tictactoe.session.service.strategy.MoveStrategies;
import com.flamingo.tictactoe.session.service.strategy.RandomMoveStrategy;
import com.flamingo.tictactoe.session.service.strategy.RuleBasedMoveStrategy;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;

import static com.flamingo.tictactoe.session.domain.BoardFixtures.board;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService(
                new InMemorySessionRepository(),
                new MoveStrategies(List.of(new RandomMoveStrategy(new Random(42)),
                        new RuleBasedMoveStrategy())),
                new InstanceIdentity("instance-test"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Nested
    class Creation {

        @Test
        void createsASessionWhoseIdAlsoIdentifiesTheGame() {
            StoredSession created = service.createSession(
                    StrategyType.RULE_BASED, StrategyType.RANDOM, 250);

            assertThat(created.session().sessionId())
                    .as("one identifier for both avoids a lookup table between the services")
                    .isEqualTo(created.session().gameId());
            assertThat(created.session().status()).isEqualTo(SessionStatus.CREATED);
            assertThat(created.session().createdAt()).isEqualTo(NOW);
            assertThat(created.version()).isZero();
        }

        @Test
        void givesEachSessionADistinctId() {
            UUID first = service.createSession(StrategyType.RANDOM, StrategyType.RANDOM, 0)
                    .session().sessionId();
            UUID second = service.createSession(StrategyType.RANDOM, StrategyType.RANDOM, 0)
                    .session().sessionId();

            assertThat(first).isNotEqualTo(second);
        }
    }

    @Nested
    class Reading {

        @Test
        void readsBackACreatedSession() {
            UUID id = service.createSession(StrategyType.RANDOM, StrategyType.RANDOM, 0)
                    .session().sessionId();

            assertThat(service.findSession(id).session().sessionId()).isEqualTo(id);
        }

        @Test
        void failsOnAnUnknownSession() {
            assertThatThrownBy(() -> service.findSession(UUID.randomUUID()))
                    .isInstanceOf(SessionNotFoundException.class);
        }
    }

    @Nested
    class Claiming {

        @Test
        void claimingMarksTheSessionRunningAndStampsThisInstance() {
            UUID id = service.createSession(StrategyType.RANDOM, StrategyType.RANDOM, 0)
                    .session().sessionId();

            StoredSession claimed = service.claimForSimulation(id);

            assertThat(claimed.session().status()).isEqualTo(SessionStatus.RUNNING);
            assertThat(claimed.session().simulationOwner()).contains("instance-test");
            assertThat(claimed.session().startedAt()).contains(NOW);
        }

        @Test
        void aSecondClaimIsRefusedAndReportsTheCurrentStatus() {
            UUID id = service.createSession(StrategyType.RANDOM, StrategyType.RANDOM, 0)
                    .session().sessionId();
            service.claimForSimulation(id);

            assertThatThrownBy(() -> service.claimForSimulation(id))
                    .asInstanceOf(InstanceOfAssertFactories.type(SimulationAlreadyStartedException.class))
                    .extracting(SimulationAlreadyStartedException::currentStatus)
                    .isEqualTo(SessionStatus.RUNNING);
        }

        @Test
        void claimingAnUnknownSessionReportsItMissingRatherThanAlreadyStarted() {
            assertThatThrownBy(() -> service.claimForSimulation(UUID.randomUUID()))
                    .isInstanceOf(SessionNotFoundException.class);
        }
    }

    @Nested
    class MoveSelection {

        @Test
        void usesTheStrategyBelongingToWhoeverIsDueToMove() {
            StoredSession stored = service.createSession(
                    StrategyType.RULE_BASED, StrategyType.RANDOM, 0);

            // X is rule-based and moves first, so the centre is the only possible answer.
            assertThat(service.chooseNextMove(stored.session())).isEqualTo(4);
        }

        @Test
        void alwaysChoosesAFreeCell() {
            StoredSession stored = service.createSession(
                    StrategyType.RANDOM, StrategyType.RANDOM, 0);
            StoredSession claimed = service.claimForSimulation(stored.session().sessionId());
            StoredSession afterMove = service.recordMove(claimed, Mark.X, 4,
                    board("XOX.X.O.."), GameOutcome.IN_PROGRESS);

            int chosen = service.chooseNextMove(afterMove.session());

            assertThat(afterMove.session().board().isFree(chosen)).isTrue();
        }
    }

    @Nested
    class Recording {

        private StoredSession running;

        @BeforeEach
        void claimASession() {
            StoredSession created = service.createSession(
                    StrategyType.RULE_BASED, StrategyType.RANDOM, 0);
            running = service.claimForSimulation(created.session().sessionId());
        }

        @Test
        void appendsMovesInOrderWithIncrementingSequenceNumbers() {
            StoredSession first = service.recordMove(running, Mark.X, 4,
                    board("....X...."), GameOutcome.IN_PROGRESS);
            StoredSession second = service.recordMove(first, Mark.O, 0,
                    board("O...X...."), GameOutcome.IN_PROGRESS);

            assertThat(second.session().moves())
                    .extracting("seq", "player", "position")
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(1, Mark.X, 4),
                            org.assertj.core.groups.Tuple.tuple(2, Mark.O, 0));
        }

        @Test
        void refreshesTheCachedBoardFromWhatTheEngineReported() {
            StoredSession afterMove = service.recordMove(running, Mark.X, 4,
                    board("....X...."), GameOutcome.IN_PROGRESS);

            assertThat(afterMove.session().board().at(4)).contains(Mark.X);
            assertThat(afterMove.session().board().freePositions()).hasSize(8);
        }

        @Test
        void markingFinishedKeepsTheOutcomeAndStampsTheTime() {
            StoredSession won = service.recordMove(running, Mark.X, 2,
                    board("XXXOO...."), GameOutcome.X_WON);

            StoredSession finished = service.markFinished(won);

            assertThat(finished.session().status()).isEqualTo(SessionStatus.FINISHED);
            assertThat(finished.session().gameOutcome()).isEqualTo(GameOutcome.X_WON);
            assertThat(finished.session().finishedAt()).contains(NOW);
        }

        @Test
        void markingFailedKeepsTheReasonAndTheMovesPlayed() {
            StoredSession afterMove = service.recordMove(running, Mark.X, 4,
                    board("....X...."), GameOutcome.IN_PROGRESS);

            StoredSession failed = service.markFailed(afterMove, "engine unreachable");

            assertThat(failed.session().status()).isEqualTo(SessionStatus.FAILED);
            assertThat(failed.session().failureReason()).contains("engine unreachable");
            assertThat(failed.session().moves()).hasSize(1);
        }
    }
}
