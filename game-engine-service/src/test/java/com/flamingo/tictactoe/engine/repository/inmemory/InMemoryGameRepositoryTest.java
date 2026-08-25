package com.flamingo.tictactoe.engine.repository.inmemory;

import com.flamingo.tictactoe.engine.repository.GameRepository;
import com.flamingo.tictactoe.engine.repository.GameRepositoryContract;
import org.junit.jupiter.api.BeforeEach;

class InMemoryGameRepositoryTest extends GameRepositoryContract {

    private InMemoryGameRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryGameRepository();
    }

    @Override
    protected GameRepository repository() {
        return repository;
    }
}
