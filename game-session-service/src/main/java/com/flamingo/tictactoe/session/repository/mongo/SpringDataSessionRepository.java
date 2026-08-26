package com.flamingo.tictactoe.session.repository.mongo;

import com.flamingo.tictactoe.session.domain.GameOutcome;
import com.flamingo.tictactoe.session.domain.SessionStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Spring Data's view of the sessions collection. Package-private so nothing outside this
 * adapter can reach past {@code SessionRepository}.
 */
interface SpringDataSessionRepository extends MongoRepository<SessionDocument, String> {

    List<SessionDocument> findByStatus(SessionStatus status);

    List<SessionDocument> findByGameOutcome(GameOutcome outcome);
}
