package com.flamingo.tictactoe.session.repository;

import com.flamingo.tictactoe.session.domain.GameOutcome;
import com.flamingo.tictactoe.session.domain.Mark;
import com.flamingo.tictactoe.session.domain.MoveRecord;
import com.flamingo.tictactoe.session.domain.Session;
import com.flamingo.tictactoe.session.domain.SessionStatus;
import com.flamingo.tictactoe.session.domain.StrategyType;
import com.flamingo.tictactoe.session.service.exception.ConcurrentSessionUpdateException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static com.flamingo.tictactoe.session.domain.BoardFixtures.board;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Behaviour every {@link SessionRepository} must provide, run against each adapter. */
public abstract class SessionRepositoryContract {

    protected static final String SESSION_ID = "session-1";
    protected static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    protected abstract SessionRepository repository();

    protected static Session newSession() {
        return Session.create(SESSION_ID, SESSION_ID, StrategyType.RULE_BASED,
                StrategyType.RANDOM, 250, NOW);
    }

    @Test
    void storesASessionAtVersionZeroAndReadsItBack() {
        StoredSession created = repository().create(newSession());

        assertThat(created.version()).isZero();
        assertThat(created.session().status()).isEqualTo(SessionStatus.CREATED);

        Session read = repository().findById(SESSION_ID).orElseThrow().session();
        assertThat(read.gameId()).isEqualTo(SESSION_ID);
        assertThat(read.xStrategy()).isEqualTo(StrategyType.RULE_BASED);
        assertThat(read.oStrategy()).isEqualTo(StrategyType.RANDOM);
        assertThat(read.moveDelayMs()).isEqualTo(250);
        assertThat(read.moves()).isEmpty();
        assertThat(read.simulationOwner()).isEmpty();
    }

    @Test
    void findingAnUnknownSessionReturnsEmpty() {
        assertThat(repository().findById("no-such-session")).isEmpty();
    }

    @Test
    void claimingMovesTheSessionToRunningAndRecordsTheOwner() {
        repository().create(newSession());

        StoredSession claimed = repository()
                .claimForSimulation(SESSION_ID, "instance-a", NOW).orElseThrow();

        assertThat(claimed.session().status()).isEqualTo(SessionStatus.RUNNING);
        assertThat(claimed.session().simulationOwner()).contains("instance-a");
        assertThat(claimed.session().startedAt()).contains(NOW);
        assertThat(claimed.version())
                .as("a claim is a write and must advance the version")
                .isEqualTo(1);
    }

    @Test
    void aSecondClaimOnTheSameSessionIsRefused() {
        repository().create(newSession());
        repository().claimForSimulation(SESSION_ID, "instance-a", NOW).orElseThrow();

        assertThat(repository().claimForSimulation(SESSION_ID, "instance-b", NOW)).isEmpty();

        assertThat(repository().findById(SESSION_ID).orElseThrow().session().simulationOwner())
                .as("the original owner must survive a second attempt")
                .contains("instance-a");
    }

    @Test
    void claimingAnUnknownSessionReturnsEmpty() {
        assertThat(repository().claimForSimulation("no-such-session", "instance-a", NOW)).isEmpty();
    }

    @Test
    void savingAppendsMovesAndAdvancesTheVersion() {
        StoredSession stored = repository().create(newSession());
        stored = repository().claimForSimulation(SESSION_ID, "instance-a", NOW).orElseThrow();

        Session withMove = stored.session().withMove(
                new MoveRecord(1, Mark.X, 4, NOW), board("....X...."), GameOutcome.IN_PROGRESS);
        StoredSession saved = repository().save(stored.withSession(withMove));

        assertThat(saved.version()).isEqualTo(2);

        Session read = repository().findById(SESSION_ID).orElseThrow().session();
        assertThat(read.moves()).singleElement().satisfies(move -> {
            assertThat(move.seq()).isEqualTo(1);
            assertThat(move.player()).isEqualTo(Mark.X);
            assertThat(move.position()).isEqualTo(4);
        });
        assertThat(read.board().at(4)).contains(Mark.X);
        assertThat(read.nextPlayer())
                .as("turn order is derived from the move count, not stored")
                .isEqualTo(Mark.O);
    }

    @Test
    void savingAtAStaleVersionIsRejected() {
        StoredSession stale = repository().create(newSession());
        repository().claimForSimulation(SESSION_ID, "instance-a", NOW).orElseThrow();

        assertThatThrownBy(() -> repository().save(
                stale.withSession(stale.session().finished(NOW))))
                .isInstanceOf(ConcurrentSessionUpdateException.class);
    }

    @Test
    void roundTripsAFinishedSessionWithItsFullHistory() {
        StoredSession stored = repository().create(newSession());
        stored = repository().claimForSimulation(SESSION_ID, "instance-a", NOW).orElseThrow();

        Session playing = stored.session();
        int[] cells = {0, 3, 1, 4, 2};
        for (int i = 0; i < cells.length; i++) {
            Mark player = i % 2 == 0 ? Mark.X : Mark.O;
            playing = playing.withMove(new MoveRecord(i + 1, player, cells[i], NOW),
                    board("XXXOO...."), i == cells.length - 1 ? GameOutcome.X_WON : GameOutcome.IN_PROGRESS);
        }
        stored = repository().save(stored.withSession(playing));
        repository().save(stored.withSession(stored.session().finished(NOW)));

        Session reloaded = repository().findById(SESSION_ID).orElseThrow().session();
        assertThat(reloaded.status()).isEqualTo(SessionStatus.FINISHED);
        assertThat(reloaded.gameOutcome()).isEqualTo(GameOutcome.X_WON);
        assertThat(reloaded.moves()).hasSize(5)
                .extracting(MoveRecord::position).containsExactly(0, 3, 1, 4, 2);
        assertThat(reloaded.finishedAt()).contains(NOW);
    }

    @Test
    void aFailedSessionKeepsItsReasonAndTheMovesPlayedSoFar() {
        StoredSession stored = repository().create(newSession());
        stored = repository().claimForSimulation(SESSION_ID, "instance-a", NOW).orElseThrow();
        stored = repository().save(stored.withSession(stored.session().withMove(
                new MoveRecord(1, Mark.X, 4, NOW), board("....X...."), GameOutcome.IN_PROGRESS)));

        repository().save(stored.withSession(stored.session().failed("engine unreachable", NOW)));

        Session reloaded = repository().findById(SESSION_ID).orElseThrow().session();
        assertThat(reloaded.status()).isEqualTo(SessionStatus.FAILED);
        assertThat(reloaded.failureReason()).contains("engine unreachable");
        assertThat(reloaded.moves())
                .as("history up to the failure must survive for diagnosis")
                .hasSize(1);
    }

    /**
     * The guarantee that stops two runners driving one game: many callers racing to start the
     * same session, exactly one wins.
     */
    @Test
    void onlyOneOfManyConcurrentClaimsSucceeds() throws Exception {
        repository().create(newSession());
        int contenders = 8;

        List<Callable<Boolean>> attempts = IntStream.range(0, contenders)
                .mapToObj(i -> (Callable<Boolean>) () ->
                        repository().claimForSimulation(SESSION_ID, "instance-" + i, NOW).isPresent())
                .toList();

        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        long winners;
        try {
            List<Future<Boolean>> results = pool.invokeAll(attempts);
            winners = results.stream().filter(future -> {
                try {
                    return future.get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).count();
        } finally {
            pool.shutdownNow();
        }

        assertThat(winners).isEqualTo(1);
        assertThat(repository().findById(SESSION_ID).orElseThrow().session().status())
                .isEqualTo(SessionStatus.RUNNING);
    }
}
