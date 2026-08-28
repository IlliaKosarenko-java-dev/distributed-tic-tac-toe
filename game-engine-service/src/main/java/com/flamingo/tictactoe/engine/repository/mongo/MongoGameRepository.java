package com.flamingo.tictactoe.engine.repository.mongo;

import com.flamingo.tictactoe.engine.mapper.GameDocumentMapper;
import com.flamingo.tictactoe.engine.repository.GameRepository;
import com.flamingo.tictactoe.engine.repository.StoredGame;
import com.flamingo.tictactoe.engine.domain.Game;
import com.flamingo.tictactoe.engine.service.exception.ConcurrentGameUpdateException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("mongo")
public class MongoGameRepository implements GameRepository {

    private final SpringDataGameRepository documents;
    private final GameDocumentMapper mapper;
    private final Clock clock;

    public MongoGameRepository(SpringDataGameRepository documents, GameDocumentMapper mapper, Clock clock) {
        this.documents = documents;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public Optional<StoredGame> findById(UUID gameId) {
        return documents.findById(gameId.toString()).map(mapper::toStoredGame);
    }

    @Override
    public Optional<StoredGame> createIfAbsent(Game game) {
        Instant now = clock.instant();
        try {
            // insert, never save: save() would upsert and silently reset a game already in
            // progress. The duplicate-key error is the point — it is what makes creation
            // idempotent without a read-then-write race.
            GameDocument inserted = documents.insert(mapper.toDocument(game, null, now, now));
            return Optional.of(mapper.toStoredGame(inserted));
        } catch (DuplicateKeyException alreadyExists) {
            return Optional.empty();
        }
    }

    @Override
    public StoredGame save(StoredGame game) {
        UUID gameId = game.game().id();
        Instant createdAt = documents.findById(gameId.toString())
                .map(GameDocument::createdAt)
                .orElse(clock.instant());

        try {
            GameDocument saved = documents.save(
                    mapper.toDocument(game.game(), game.version(), createdAt, clock.instant()));
            return mapper.toStoredGame(saved);
        } catch (OptimisticLockingFailureException lostTheRace) {
            // Another writer advanced the version between our read and this write.
            throw new ConcurrentGameUpdateException(gameId, game.version());
        }
    }
}
