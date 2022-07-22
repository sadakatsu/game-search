package com.sadakatsu.game.domain;

import com.sadakatsu.game.domain.Standings.Initializer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.sadakatsu.game.domain.Generator.*;
import static org.junit.jupiter.api.Assertions.*;

public class StandingsInitializerTest {
    @Nested
    public class FactoryTest {
        @Test
        public void mustRejectNullPlayer() {
            assertThrows(
                NullPointerException.class,
                () -> Initializer.of(
                    null,
                    generateRandomScore()
                )
            );
        }

        @Test
        public void mustRejectNullScore() {
            assertThrows(
                NullPointerException.class,
                () -> Initializer.of(
                    selectRandomPlayer(),
                    (Integer) null
                )
            );
        }

        @Test
        public void mustInstantiateWithValidArguments() {
            final var initializer = generateRandomInitializer();
            assertNotNull(initializer);
        }
    }

    @Nested
    public class ConstructorTest {
        @Test
        public void mustRejectNullPlayer() {
            assertThrows(
                NullPointerException.class,
                () -> new Initializer<>(
                    null,
                    generateRandomScore()
                )
            );
        }

        @Test
        public void mustRejectNullScore() {
            assertThrows(
                NullPointerException.class,
                () -> new Initializer<>(
                    selectRandomPlayer(),
                    (Integer) null
                )
            );
        }

        @Test
        public void mustInstantiateWithValidArguments() {
            final var player = selectRandomPlayer();
            final var score = generateRandomScore();
            final var initializer = new Initializer<>(player, score);
            assertNotNull(initializer);
        }
    }

    @Nested
    public class GetKeyTest {
        @Test
        public void mustReturnPlayerPassedToConstructor() {
            for (final var player : PLAYERS) {
                final var score = generateRandomScore();
                final var initializer = Initializer.of(player, score);
                assertEquals(player, initializer.getKey());
            }
        }
    }

    @Nested
    public class GetValueTest {
        @Test
        public void mustReturnScorePassedToConstructor() {
            for (var i = 0; i < REPETITIONS; ++i) {
                final var player = selectRandomPlayer();
                final var score = generateRandomScore();
                final var initializer = Initializer.of(player, score);
                assertEquals(score, initializer.getValue());
            }
        }
    }

    @Nested
    public class SetValueTest {
        @Test
        public void mustRejectAnyCall() {
            for (var i = 0; i < REPETITIONS; ++i) {
                final var initializer = generateRandomInitializer();
                final var score = generateRandomScore();
                assertThrows(
                    UnsupportedOperationException.class,
                    () -> initializer.setValue(score)
                );
            }
        }
    }

    @Nested
    public class KeyTest {
        @Test
        public void mustReturnPlayerPassedToConstructor() {
            for (final var player : PLAYERS) {
                final var score = generateRandomScore();
                final var initializer = Initializer.of(player, score);
                assertEquals(player, initializer.key());
            }
        }
    }

    @Nested
    public class ValueTest {
        @Test
        public void mustReturnScorePassedToConstructor() {
            for (var i = 0; i < REPETITIONS; ++i) {
                final var player = selectRandomPlayer();
                final var score = generateRandomScore();
                final var initializer = Initializer.of(player, score);
                assertEquals(score, initializer.value());
            }
        }
    }

    private static final int REPETITIONS = 1000;
}
