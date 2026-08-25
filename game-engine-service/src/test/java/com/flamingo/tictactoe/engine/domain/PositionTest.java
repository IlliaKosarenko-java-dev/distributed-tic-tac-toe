package com.flamingo.tictactoe.engine.domain;

import com.flamingo.tictactoe.engine.domain.exception.InvalidPositionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PositionTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8})
    void acceptsEveryCellOnTheBoard(int index) {
        assertThat(Position.of(index).index()).isEqualTo(index);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 9, 42, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void rejectsIndexesOutsideTheBoard(int index) {
        assertThatThrownBy(() -> Position.of(index))
                .isInstanceOf(InvalidPositionException.class)
                .hasMessageContaining("outside the board");
    }

    @Test
    void reportsTheOffendingIndexSoTheWebLayerCanEchoIt() {
        assertThatThrownBy(() -> Position.of(12))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(InvalidPositionException.class))
                .extracting(InvalidPositionException::index)
                .isEqualTo(12);
    }

    @Test
    void positionsWithTheSameIndexAreEqual() {
        assertThat(Position.of(4)).isEqualTo(new Position(4));
    }
}
