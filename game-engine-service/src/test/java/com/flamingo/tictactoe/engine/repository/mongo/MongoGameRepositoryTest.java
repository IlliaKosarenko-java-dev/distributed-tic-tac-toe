package com.flamingo.tictactoe.engine.repository.mongo;

import java.util.UUID;
import com.flamingo.tictactoe.engine.mapper.GameDocumentMapper;
import com.flamingo.tictactoe.engine.repository.GameRepository;
import com.flamingo.tictactoe.engine.repository.GameRepositoryContract;
import com.flamingo.tictactoe.engine.repository.StoredGame;
import com.flamingo.tictactoe.engine.config.ClockConfiguration;
import com.flamingo.tictactoe.engine.domain.Game;
import com.flamingo.tictactoe.engine.domain.Move;
import com.flamingo.tictactoe.engine.domain.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the shared repository contract against a real MongoDB, plus the behaviour that only
 * exists once there is a document store underneath.
 */
@DataMongoTest
@Testcontainers
@ActiveProfiles("mongo")
@Import({MongoGameRepository.class, GameDocumentMapper.class, ClockConfiguration.class})
class MongoGameRepositoryTest extends GameRepositoryContract {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    private static final UUID SECOND_GAME_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MongoGameRepository repository;

    @Autowired
    private SpringDataGameRepository documents;

    @BeforeEach
    void clearCollection() {
        documents.deleteAll();
    }

    @Override
    protected GameRepository repository() {
        return repository;
    }

    @Test
    void writesOneDocumentPerGame() {
        repository.createIfAbsent(Game.newGame(GAME_ID, Player.X));
        repository.createIfAbsent(Game.newGame(SECOND_GAME_ID, Player.O));

        assertThat(documents.count()).isEqualTo(2);
    }

    @Test
    void storesTheVersionOnTheDocumentItself() {
        StoredGame created = repository.createIfAbsent(Game.newGame(GAME_ID, Player.X)).orElseThrow();
        repository.save(created.withGame(created.game().applyMove(Move.of(Player.X, 0))));

        assertThat(documents.findById(GAME_ID.toString()).orElseThrow().version())
                .as("the concurrency token must live in the document, not in the JVM")
                .isEqualTo(1L);
    }

    @Test
    void keepsTheOriginalCreationTimestampAcrossMoves() {
        StoredGame created = repository.createIfAbsent(Game.newGame(GAME_ID, Player.X)).orElseThrow();
        var createdAt = documents.findById(GAME_ID.toString()).orElseThrow().createdAt();

        repository.save(created.withGame(created.game().applyMove(Move.of(Player.X, 0))));

        assertThat(documents.findById(GAME_ID.toString()).orElseThrow().createdAt()).isEqualTo(createdAt);
    }
}
