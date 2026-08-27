package com.flamingo.tictactoe.session.repository.mongo;

import com.flamingo.tictactoe.session.domain.Session;
import com.flamingo.tictactoe.session.domain.SessionStatus;
import com.flamingo.tictactoe.session.mapper.SessionDocumentMapper;
import com.flamingo.tictactoe.session.repository.SessionQuery;
import com.flamingo.tictactoe.session.repository.SessionRepository;
import com.flamingo.tictactoe.session.repository.StoredSession;
import com.flamingo.tictactoe.session.service.exception.ConcurrentSessionUpdateException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("mongo")
public class MongoSessionRepository implements SessionRepository {

    private final SpringDataSessionRepository documents;
    private final MongoTemplate mongoTemplate;
    private final SessionDocumentMapper mapper;
    private final Clock clock;

    public MongoSessionRepository(SpringDataSessionRepository documents, MongoTemplate mongoTemplate,
                                  SessionDocumentMapper mapper, Clock clock) {
        this.documents = documents;
        this.mongoTemplate = mongoTemplate;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public Optional<StoredSession> findById(String sessionId) {
        return documents.findById(sessionId).map(mapper::toStoredSession);
    }

    @Override
    public StoredSession create(Session session) {
        return mapper.toStoredSession(documents.insert(mapper.toDocument(session, null)));
    }

    /**
     * Claims the session with a single conditional update. The status is part of the query, so
     * the document only changes if it is still CREATED — no read-then-write window for a second
     * caller to slip into.
     */
    @Override
    public Optional<StoredSession> claimForSimulation(String sessionId, String owner, Instant startedAt) {
        Query onlyIfUnclaimed = Query.query(Criteria.where("_id").is(sessionId)
                .and("status").is(SessionStatus.CREATED));

        Update claim = new Update()
                .set("status", SessionStatus.RUNNING)
                .set("simulationOwner", owner)
                .set("startedAt", startedAt)
                // findAndModify bypasses Spring Data's @Version handling, so the version has
                // to be advanced by hand or a later save() would write against a stale number.
                .inc("version", 1);

        SessionDocument claimed = mongoTemplate.findAndModify(
                onlyIfUnclaimed, claim, FindAndModifyOptions.options().returnNew(true),
                SessionDocument.class);

        return Optional.ofNullable(claimed).map(mapper::toStoredSession);
    }

    @Override
    public StoredSession save(StoredSession session) {
        String sessionId = session.session().sessionId();
        try {
            return mapper.toStoredSession(
                    documents.save(mapper.toDocument(session.session(), session.version())));
        } catch (OptimisticLockingFailureException lostTheRace) {
            throw new ConcurrentSessionUpdateException(sessionId, session.version());
        }
    }

    /**
     * Filters and sorts in the database rather than in memory, which is the point of having a
     * queryable store: the indexes on status and outcome do the work.
     */
    @Override
    public List<StoredSession> search(SessionQuery query) {
        Criteria criteria = new Criteria();
        if (query.status() != null) {
            criteria = criteria.and("status").is(query.status());
        }
        if (query.outcome() != null) {
            criteria = criteria.and("gameOutcome").is(query.outcome());
        }
        if (query.xStrategy() != null) {
            criteria = criteria.and("xStrategy").is(query.xStrategy());
        }
        if (query.oStrategy() != null) {
            criteria = criteria.and("oStrategy").is(query.oStrategy());
        }

        Query mongoQuery = new Query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .limit(query.limit());

        return mongoTemplate.find(mongoQuery, SessionDocument.class).stream()
                .map(mapper::toStoredSession)
                .toList();
    }

    Instant now() {
        return clock.instant();
    }
}
