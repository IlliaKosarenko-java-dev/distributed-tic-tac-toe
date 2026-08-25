package com.flamingo.tictactoe.engine.repository;

import com.flamingo.tictactoe.engine.domain.Game;
import com.flamingo.tictactoe.engine.domain.GameStatus;
import com.flamingo.tictactoe.engine.domain.Move;
import com.flamingo.tictactoe.engine.domain.Player;
import com.flamingo.tictactoe.engine.domain.Position;
import com.flamingo.tictactoe.engine.service.exception.ConcurrentGameUpdateException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The behaviour every {@link GameRepository} must provide, run against each adapter.
 */
public abstract class GameRepositoryContract {

    protected static final String GAME_ID = "game-1";

    protected abstract GameRepository repository();

    @Test
    void storesAGameAtVersionZeroAndReadsItBack() {
        StoredGame created = repository().createIfAbsent(Game.newGame(GAME_ID, Player.X)).orElseThrow();

        assertThat(created.version()).isZero();
        assertThat(created.game().status()).isEqualTo(GameStatus.IN_PROGRESS);

        StoredGame read = repository().findById(GAME_ID).orElseThrow();
        assertThat(read.game().id()).isEqualTo(GAME_ID);
        assertThat(read.game().moveCount()).isZero();
        assertThat(read.game().nextPlayer()).isEqualTo(Player.X);
        assertThat(read.game().board().freePositions()).hasSize(9);
    }

    @Test
    void findingAnUnknownGameReturnsEmpty() {
        assertThat(repository().findById("no-such-game")).isEmpty();
    }

    @Test
    void createIfAbsentRefusesToOverwriteAGameInProgress() {
        StoredGame created = repository().createIfAbsent(Game.newGame(GAME_ID, Player.X)).orElseThrow();
        repository().save(created.withGame(created.game().applyMove(Move.of(Player.X, 4))));

        assertThat(repository().createIfAbsent(Game.newGame(GAME_ID, Player.O))).isEmpty();

        StoredGame survived = repository().findById(GAME_ID).orElseThrow();
        assertThat(survived.game().moveCount())
                .as("a retried create must not reset a game already under way")
                .isEqualTo(1);
    }

    @Test
    void savingAdvancesTheVersion() {
        StoredGame created = repository().createIfAbsent(Game.newGame(GAME_ID, Player.X)).orElseThrow();

        StoredGame saved = repository().save(created.withGame(created.game().applyMove(Move.of(Player.X, 0))));

        assertThat(saved.version()).isEqualTo(1);
        assertThat(repository().findById(GAME_ID).orElseThrow().version()).isEqualTo(1);
    }

    @Test
    void savingAtAStaleVersionIsRejected() {
        StoredGame stale = repository().createIfAbsent(Game.newGame(GAME_ID, Player.X)).orElseThrow();
        repository().save(stale.withGame(stale.game().applyMove(Move.of(Player.X, 0))));

        assertThatThrownBy(() -> repository().save(stale.withGame(stale.game().applyMove(Move.of(Player.X, 1)))))
                .isInstanceOf(ConcurrentGameUpdateException.class);

        assertThat(repository().findById(GAME_ID).orElseThrow().game().board().at(Position.of(1)))
                .as("the rejected write must not have landed")
                .isEmpty();
    }

    @Test
    void roundTripsAFinishedGameIncludingItsWinningLine() {
        StoredGame current = repository().createIfAbsent(Game.newGame(GAME_ID, Player.X)).orElseThrow();
        for (int cell : new int[]{0, 3, 1, 4, 2}) {
            current = repository().save(current.withGame(
                    current.game().applyMove(Move.of(current.game().nextPlayer(), cell))));
        }

        Game reloaded = repository().findById(GAME_ID).orElseThrow().game();

        assertThat(reloaded.status()).isEqualTo(GameStatus.X_WON);
        assertThat(reloaded.moveCount()).isEqualTo(5);
        assertThat(reloaded.winningLine()).hasValueSatisfying(line -> {
            assertThat(line.player()).isEqualTo(Player.X);
            assertThat(line.positions()).containsExactlyInAnyOrder(0, 1, 2);
        });
        assertThat(reloaded.board().at(Position.of(3))).contains(Player.O);
        assertThat(reloaded.board().at(Position.of(5)))
                .as("free cells must survive the round trip as free")
                .isEmpty();
    }

    /**
     * The guarantee the whole design rests on: readers that saw the same version cannot both
     * write. Exactly one move survives, so a game can never gain two marks in one turn.
     */
    @Test
    void onlyOneOfManyConcurrentWritersAtTheSameVersionWins() throws Exception {
        StoredGame shared = repository().createIfAbsent(Game.newGame(GAME_ID, Player.X)).orElseThrow();
        int writers = 9;

        List<Callable<Boolean>> attempts = IntStream.range(0, writers)
                .mapToObj(cell -> (Callable<Boolean>) () -> {
                    try {
                        repository().save(shared.withGame(shared.game().applyMove(Move.of(Player.X, cell))));
                        return true;
                    } catch (ConcurrentGameUpdateException expected) {
                        return false;
                    }
                })
                .toList();

        ExecutorService pool = Executors.newFixedThreadPool(writers);
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

        StoredGame finalState = repository().findById(GAME_ID).orElseThrow();
        assertThat(finalState.version()).isEqualTo(1);
        assertThat(finalState.game().moveCount()).isEqualTo(1);
        assertThat(finalState.game().board().freePositions()).hasSize(8);
    }
}
