package com.flamingo.tictactoe.session.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.flamingo.tictactoe.session.domain.BoardFixtures.board;
import static org.assertj.core.api.Assertions.assertThat;

class SessionTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-01-01T10:00:30Z");

    private static Session session() {
        return Session.create("s1", "s1", StrategyType.RULE_BASED, StrategyType.RANDOM, 250, NOW);
    }

    @Nested
    class Creation {

        @Test
        void startsEmptyAndUnclaimed() {
            Session created = session();

            assertThat(created.status()).isEqualTo(SessionStatus.CREATED);
            assertThat(created.gameOutcome()).isEqualTo(GameOutcome.IN_PROGRESS);
            assertThat(created.moves()).isEmpty();
            assertThat(created.board().freePositions()).hasSize(9);
            assertThat(created.simulationOwner()).isEmpty();
            assertThat(created.startedAt()).isEmpty();
            assertThat(created.finishedAt()).isEmpty();
        }

        @Test
        void mapsEachPlayerToItsOwnStrategy() {
            Session created = session();

            assertThat(created.strategyFor(Mark.X)).isEqualTo(StrategyType.RULE_BASED);
            assertThat(created.strategyFor(Mark.O)).isEqualTo(StrategyType.RANDOM);
        }
    }

    @Nested
    class TurnOrder {

        @Test
        void derivesTheNextPlayerFromTheMoveCount() {
            Session playing = session();
            assertThat(playing.nextPlayer()).isEqualTo(Mark.X);

            playing = playing.withMove(new MoveRecord(1, Mark.X, 4, NOW),
                    board("....X...."), GameOutcome.IN_PROGRESS);
            assertThat(playing.nextPlayer()).isEqualTo(Mark.O);

            playing = playing.withMove(new MoveRecord(2, Mark.O, 0, NOW),
                    board("O...X...."), GameOutcome.IN_PROGRESS);
            assertThat(playing.nextPlayer())
                    .as("turn order must not need its own stored field to stay consistent")
                    .isEqualTo(Mark.X);
        }
    }

    @Nested
    class Transitions {

        @Test
        void claimingRecordsTheOwnerWithoutTouchingTheBoard() {
            Session claimed = session().claimedBy("instance-a", LATER);

            assertThat(claimed.status()).isEqualTo(SessionStatus.RUNNING);
            assertThat(claimed.simulationOwner()).contains("instance-a");
            assertThat(claimed.startedAt()).contains(LATER);
            assertThat(claimed.moves()).isEmpty();
        }

        @Test
        void recordingAMoveReturnsANewSessionAndLeavesTheOldOneAlone() {
            Session before = session().claimedBy("instance-a", NOW);

            Session after = before.withMove(new MoveRecord(1, Mark.X, 4, NOW),
                    board("....X...."), GameOutcome.IN_PROGRESS);

            assertThat(after).isNotSameAs(before);
            assertThat(before.moveCount()).isZero();
            assertThat(after.moveCount()).isEqualTo(1);
            assertThat(after.board().at(4)).contains(Mark.X);
        }

        @Test
        void finishingKeepsTheHistoryAndOutcome() {
            Session played = session().claimedBy("instance-a", NOW)
                    .withMove(new MoveRecord(1, Mark.X, 4, NOW), board("....X...."), GameOutcome.X_WON);

            Session finished = played.finished(LATER);

            assertThat(finished.status()).isEqualTo(SessionStatus.FINISHED);
            assertThat(finished.status().isTerminal()).isTrue();
            assertThat(finished.gameOutcome()).isEqualTo(GameOutcome.X_WON);
            assertThat(finished.moves()).hasSize(1);
            assertThat(finished.finishedAt()).contains(LATER);
        }

        @Test
        void failingKeepsTheReasonAndWhatWasPlayedSoFar() {
            Session played = session().claimedBy("instance-a", NOW)
                    .withMove(new MoveRecord(1, Mark.X, 4, NOW), board("....X...."), GameOutcome.IN_PROGRESS);

            Session failed = played.failed("engine unreachable", LATER);

            assertThat(failed.status()).isEqualTo(SessionStatus.FAILED);
            assertThat(failed.failureReason()).contains("engine unreachable");
            assertThat(failed.moves()).hasSize(1);
        }
    }

    @Test
    void theMoveListCannotBeMutatedThroughTheAccessor() {
        Session played = session();

        assertThat(played.moves()).isUnmodifiable();
    }
}
