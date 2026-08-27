package com.flamingo.tictactoe.session.dto;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulationModeTest {

    @ParameterizedTest(name = "\"{0}\" parses as {1}")
    @CsvSource({
            "async, ASYNC", "ASYNC, ASYNC", "Async, ASYNC", "'  async  ', ASYNC",
            "sync, SYNC", "SYNC, SYNC", "Sync, SYNC"
    })
    void acceptsEitherModeInAnyCaseAndIgnoresSurroundingSpace(String raw, SimulationMode expected) {
        assertThat(SimulationMode.of(raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" is rejected")
    @ValueSource(strings = {"sideways", "", "asyn", "async sync", "0"})
    void rejectsAnythingElseWithAMessageNamingTheOptions(String raw) {
        assertThatThrownBy(() -> SimulationMode.of(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'async' or 'sync'")
                .hasMessageContaining(raw);
    }

    @ParameterizedTest
    @NullSource
    void rejectsNullWithoutLeakingANullPointerException(String raw) {
        assertThatThrownBy(() -> SimulationMode.of(raw))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
