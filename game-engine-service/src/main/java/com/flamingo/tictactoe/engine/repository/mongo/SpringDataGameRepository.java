package com.flamingo.tictactoe.engine.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

import com.flamingo.tictactoe.engine.domain.GameStatus;


interface SpringDataGameRepository extends MongoRepository<GameDocument, String> {

    List<GameDocument> findByStatus(GameStatus status);
}
