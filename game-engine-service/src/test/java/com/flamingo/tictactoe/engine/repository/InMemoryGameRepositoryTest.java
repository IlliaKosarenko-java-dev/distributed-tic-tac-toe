package com.flamingo.tictactoe.engine.repository;

import com.flamingo.tictactoe.engine.service.exception.ConcurrentGameUpdateException;
import com.flamingo.tictactoe.engine.domain.Game;
import com.flamingo.tictactoe.engine.domain.Move;
import com.flamingo.tictactoe.engine.domain.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryGameRepositoryTest {

    private static final String GAME_ID = "game-1";

    private InMemoryGameRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryGameRepository();
    }

    @Test
    void storesAndReadsBackAGame() {
        StoredGame created = repository.createIfAbsent(Game.newGame(GAME_ID, Player.X)).orElseThrow();

        assertThat(created.version()).isZero();
        assertThat(repository.findById(GAME_ID)).contains(created);
    }

    @Test
    void findingAnUnknownGameReturnsEmpty() {
        assertThat(repository.findById("nope")).isEmpty();
    }

    @Test
    void createIfAbsentRefusesToOverwriteAnExistingGame() {
        repository.createIfAbsent(Game.newGame(GAME_ID, Player.X)).orElseThrow();

        assertThat(repository.createIfAbsent(Game.newGame(GAME_ID, Player.O))).isEmpty();
        assertThat(repository.findById(GAME_ID).orElseThrow().game().nextPlayer()).isEqualTo(Player.X);
    }

    @Test
    void savingAdvancesTheVersion() {
        StoredGame stored = repository.createIfAbsent(Game.newGame(GAME_ID, Player.X)).orElseThrow();

        StoredGame saved = repository.save(stored.withGame(stored.game().applyMove(Move.of(Player.X, 0))));

        assertThat(saved.version()).isEqualTo(1);
        assertThat(repository.findById(GAME_ID).orElseThrow().version()).isEqualTo(1);
    }

    @Test
    void savingAtAStaleVersionIsRejected() {
        StoredGame stale = repository.createIfAbsent(Game.newGame(GAME_ID, Player.X)).orElseThrow();
        repository.save(stale.withGame(stale.game().applyMove(Move.of(Player.X, 0))));

        assertThatThrownBy(() -> repository.save(stale.withGame(stale.game().applyMove(Move.of(Player.X, 1)))))
                .isInstanceOf(ConcurrentGameUpdateException.class);
    }

    /**
     * The guarantee the MongoDB adapter will have to reproduce: concurrent writers reading
     * the same version must not both succeed.
     */
    @Test
    void onlyOneOfManyConcurrentWritersAtTheSameVersionWins() throws Exception {
        StoredGame stored = repository.createIfAbsent(Game.newGame(GAME_ID, Player.X)).orElseThrow();
        int writers = 9;

        List<Callable<Boolean>> attempts = IntStream.range(0, writers)
                .mapToObj(cell -> (Callable<Boolean>) () -> {
                    try {
                        repository.save(stored.withGame(stored.game().applyMove(Move.of(Player.X, cell))));
                        return true;
                    } catch (ConcurrentGameUpdateException expected) {
                        return false;
                    }
                })
                .toList();

        // ExecutorService is only AutoCloseable from Java 19; this module targets 17.
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        try {
            List<Future<Boolean>> results = pool.invokeAll(attempts);

            long winners = results.stream().filter(future -> {
                try {
                    return future.get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).count();

            assertThat(winners).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        StoredGame finalState = repository.findById(GAME_ID).orElseThrow();
        assertThat(finalState.version()).isEqualTo(1);
        assertThat(finalState.game().moveCount()).isEqualTo(1);
    }
}
