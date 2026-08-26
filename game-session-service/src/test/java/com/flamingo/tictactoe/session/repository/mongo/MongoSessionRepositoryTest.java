package com.flamingo.tictactoe.session.repository.mongo;

import com.flamingo.tictactoe.session.config.SessionConfiguration;
import com.flamingo.tictactoe.session.domain.GameOutcome;
import com.flamingo.tictactoe.session.domain.Mark;
import com.flamingo.tictactoe.session.domain.MoveRecord;
import com.flamingo.tictactoe.session.domain.SessionStatus;
import com.flamingo.tictactoe.session.mapper.SessionDocumentMapper;
import com.flamingo.tictactoe.session.repository.SessionRepository;
import com.flamingo.tictactoe.session.repository.SessionRepositoryContract;
import com.flamingo.tictactoe.session.repository.StoredSession;
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

import static com.flamingo.tictactoe.session.domain.BoardFixtures.board;
import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Testcontainers
@ActiveProfiles("mongo")
@Import({MongoSessionRepository.class, SessionDocumentMapper.class, SessionConfiguration.class})
class MongoSessionRepositoryTest extends SessionRepositoryContract {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Autowired
    private MongoSessionRepository repository;

    @Autowired
    private SpringDataSessionRepository documents;

    @BeforeEach
    void clearCollection() {
        documents.deleteAll();
    }

    @Override
    protected SessionRepository repository() {
        return repository;
    }

    @Test
    void storesTheMoveHistoryInsideTheSessionDocument() {
        StoredSession stored = repository.create(newSession());
        stored = repository.claimForSimulation(SESSION_ID, "instance-a", NOW).orElseThrow();
        repository.save(stored.withSession(stored.session().withMove(
                new MoveRecord(1, Mark.X, 4, NOW), board("....X...."), GameOutcome.IN_PROGRESS)));

        SessionDocument document = documents.findById(SESSION_ID).orElseThrow();

        assertThat(documents.count())
                .as("history is embedded, not a second collection")
                .isEqualTo(1);
        assertThat(document.moves()).singleElement().satisfies(entry -> {
            assertThat(entry.seq()).isEqualTo(1);
            assertThat(entry.player()).isEqualTo(Mark.X);
            assertThat(entry.position()).isEqualTo(4);
        });
    }

    @Test
    void theClaimAdvancesTheStoredVersionSoLaterSavesStillWork() {
        repository.create(newSession());

        StoredSession claimed = repository.claimForSimulation(SESSION_ID, "instance-a", NOW).orElseThrow();

        assertThat(documents.findById(SESSION_ID).orElseThrow().version())
                .as("findAndModify bypasses @Version, so the claim must bump it itself")
                .isEqualTo(claimed.version());

        // Would throw if the in-memory view and the stored version had drifted apart.
        repository.save(claimed.withSession(claimed.session().finished(NOW)));
        assertThat(repository.findById(SESSION_ID).orElseThrow().session().status())
                .isEqualTo(SessionStatus.FINISHED);
    }

    @Test
    void indexedFieldsSupportQueryingSessionsByStatusAndOutcome() {
        StoredSession finished = repository.create(newSession());
        finished = repository.claimForSimulation(SESSION_ID, "instance-a", NOW).orElseThrow();
        finished = repository.save(finished.withSession(finished.session().withMove(
                new MoveRecord(1, Mark.X, 4, NOW), board("XXXOO...."), GameOutcome.X_WON)));
        repository.save(finished.withSession(finished.session().finished(NOW)));

        repository.create(com.flamingo.tictactoe.session.domain.Session.create(
                "session-2", "session-2",
                com.flamingo.tictactoe.session.domain.StrategyType.RANDOM,
                com.flamingo.tictactoe.session.domain.StrategyType.RANDOM, 0, NOW));

        assertThat(documents.findByStatus(SessionStatus.FINISHED))
                .extracting(SessionDocument::id).containsExactly(SESSION_ID);
        assertThat(documents.findByGameOutcome(GameOutcome.X_WON))
                .extracting(SessionDocument::id).containsExactly(SESSION_ID);
        assertThat(documents.findByStatus(SessionStatus.CREATED))
                .extracting(SessionDocument::id).containsExactly("session-2");
    }

    @Test
    void aSessionSurvivesBeingReadThroughAFreshRepositoryInstance() {
        repository.create(newSession());
        repository.claimForSimulation(SESSION_ID, "instance-a", NOW).orElseThrow();

        SessionRepository reopened = new MongoSessionRepository(
                documents, mongoTemplate(), new SessionDocumentMapper(), java.time.Clock.systemUTC());

        assertThat(reopened.findById(SESSION_ID).orElseThrow().session().status())
                .isEqualTo(SessionStatus.RUNNING);
    }

    @Autowired
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate() {
        return mongoTemplate;
    }
}
