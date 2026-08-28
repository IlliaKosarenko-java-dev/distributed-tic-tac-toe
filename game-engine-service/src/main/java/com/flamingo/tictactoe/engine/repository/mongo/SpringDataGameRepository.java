package com.flamingo.tictactoe.engine.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataGameRepository extends MongoRepository<GameDocument, String> {
}
