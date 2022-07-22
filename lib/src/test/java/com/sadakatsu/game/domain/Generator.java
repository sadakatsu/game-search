package com.sadakatsu.game.domain;

import com.google.common.collect.Lists;
import com.sadakatsu.game.domain.Standings.Initializer;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@UtilityClass
public class Generator {
    public enum FooPlayer implements Player {
        FIRST,
        SECOND,
        THIRD,
        FOURTH,
        FIFTH
    }

    public static List<FooPlayer> PLAYERS = Lists.newArrayList(FooPlayer.values());
    public static int PLAYER_COUNT = PLAYERS.size();

    public static FooPlayer selectRandomPlayer() {
        final var random = ThreadLocalRandom.current();
        final var index = random.nextInt(PLAYER_COUNT);
        return PLAYERS.get(index);
    }

    public static int generateRandomScore() {
        final var random = ThreadLocalRandom.current();
        return random.nextInt(1_001);
    }

    public static Initializer<FooPlayer, Integer> generateRandomInitializer() {
        final var player = selectRandomPlayer();
        final var score = generateRandomScore();
        return Initializer.of(player, score);
    }

    public static Standings<FooPlayer, Integer> generateRandomStandings() {
        return generateRandomStandings(
            generateRandomBoolean()
        );
    }

    public static Standings<FooPlayer, Integer> generateRandomStandings(boolean maximizeScore) {
        final List<Initializer<FooPlayer, Integer>> initializers = Lists.newArrayList();
        for (final var player : PLAYERS) {
            final var initializer = Initializer.of(player, generateRandomScore());
            initializers.add(initializer);
        }
        return new Standings<>(maximizeScore, (lhs, rhs) -> lhs - rhs, initializers);
    }

    public static boolean generateRandomBoolean() {
        return ThreadLocalRandom.current().nextBoolean();
    }
}
