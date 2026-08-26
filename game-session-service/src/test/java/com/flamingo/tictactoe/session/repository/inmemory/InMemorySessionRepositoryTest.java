package com.flamingo.tictactoe.session.repository.inmemory;

import com.flamingo.tictactoe.session.repository.SessionRepository;
import com.flamingo.tictactoe.session.repository.SessionRepositoryContract;
import org.junit.jupiter.api.BeforeEach;

class InMemorySessionRepositoryTest extends SessionRepositoryContract {

    private InMemorySessionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemorySessionRepository();
    }

    @Override
    protected SessionRepository repository() {
        return repository;
    }
}
