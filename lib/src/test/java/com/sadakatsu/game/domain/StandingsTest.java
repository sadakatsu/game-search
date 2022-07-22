package com.sadakatsu.game.domain;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.sadakatsu.game.domain.Generator.FooPlayer;
import com.sadakatsu.game.domain.Standings.Initializer;
import com.sadakatsu.game.exceptions.InvalidPlayerException;
import com.sadakatsu.game.exceptions.MismatchedPlaythroughException;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static com.sadakatsu.game.domain.Generator.*;
import static org.junit.jupiter.api.Assertions.*;

public class StandingsTest {
    @Nested
    public class ConstructorTest {
        @Test
        public void mustRejectNullSubtract() {
            assertThrows(
                NullPointerException.class,
                () -> new Standings<>(
                    true,
                    null,
                    Initializer.of(Generator.FooPlayer.FIRST, 1),
                    Initializer.of(Generator.FooPlayer.SECOND, 2)
                )
            );
        }

        @Test
        public void mustRejectNullEntriesArray() {
            assertThrows(
                NullPointerException.class,
                () -> new Standings<>(
                    true,
                    (lhs, rhs) -> lhs - rhs,
                    (Initializer<FooPlayer, Integer>[]) null
                )
            );
        }

        @Test
        public void mustRejectNullEntriesIterable() {
            assertThrows(
                NullPointerException.class,
                () -> new Standings<>(
                    true,
                    (lhs, rhs) -> lhs - rhs,
                    (Iterable<Initializer<FooPlayer, Integer>>) null
                )
            );
        }

        @SuppressWarnings("unchecked")
        @Test
        public void mustRejectEmptyEntriesArray() {
            assertThrows(
                IllegalArgumentException.class,
                () -> new Standings<>(
                    true,
                    (lhs, rhs) -> lhs - rhs,
                    (Initializer<FooPlayer, Integer>[]) new Initializer[0]
                )
            );
        }

        @Test
        public void mustRejectEmptyEntriesIterable() {
            assertThrows(
                IllegalArgumentException.class,
                () -> new Standings<FooPlayer, Integer>(
                    true,
                    (lhs, rhs) -> lhs - rhs,
                    Collections.emptyList()
                )
            );
        }

        @Test
        public void mustRejectNullInitializer() {
            assertThrows(
                IllegalArgumentException.class,
                () -> new Standings<>(
                    true,
                    (lhs, rhs) -> lhs - rhs,
                    (Initializer<FooPlayer, Integer>) null
                )
            );
        }

        @Test
        public void mustRejectInitializersDuplicatingPlayer() {
            final var player = selectRandomPlayer();
            assertThrows(
                IllegalArgumentException.class,
                () -> new Standings<>(
                    true,
                    (lhs, rhs) -> lhs - rhs,
                    Initializer.of(player, generateRandomScore()),
                    Initializer.of(player, generateRandomScore())
                )
            );
        }

        @Test
        public void mustRejectSolitaireGameThatDoesNotMaximizeScore() {
            final var player = selectRandomPlayer();
            assertThrows(
                IllegalArgumentException.class,
                () -> new Standings<>(
                    false,
                    (lhs, rhs) -> lhs - rhs,
                    Initializer.of(player, generateRandomScore())
                )
            );
        }

        @Test
        public void mustAcceptMaximizeScoreVariantWithOneOrMorePlayers() {
            final List<Initializer<FooPlayer, Integer>> initializers = Lists.newArrayList();
            for (final var player : PLAYERS) {
                final var initializer = Initializer.of(player, generateRandomScore());
                initializers.add(initializer);
                final var standings = new Standings<>(true, (lhs, rhs) -> lhs - rhs, initializers);
                assertNotNull(standings);
            }
        }

        @Test
        public void mustAcceptScorelessVariantWithTwoOrMorePlayers() {
            final List<Initializer<FooPlayer, Integer>> initializers = Lists.newArrayList();
            for (final var player : PLAYERS) {
                final var initializer = Initializer.of(player, generateRandomScore());
                initializers.add(initializer);
                if (initializers.size() == 1) {
                    continue;
                }
                final var standings = new Standings<>(false, (lhs, rhs) -> lhs - rhs, initializers);
                assertNotNull(standings);
            }
        }
    }

    @TestFactory
    public Stream<DynamicTest> getCompetitiveScoreTest() {
        // Developer's Note: (J. Craig, 2022-07-19)
        // You can calculate all the possible combinations of outcome and player perspective easily.
        // Assume that every outcome is a sorted list of placements (rankings), descending from best to worst.
        // First place has the most outcomes, as there must always be a player in first place.  As each place after
        // first either ties with the place before it or is worse than it, first place has 2^(N-1) possible
        // outcomes.  These are also all the unique outcomes that can occur.
        // Each place after first only appears in exactly half of these placements due to ties causing some
        // placements to disappear (e.g., [1, 1, 3, 4] does not have second place).
        // This means that the total number of outcome/perspective combinations is equal to 2^(N-1)+(N-1)*2^(N-2).
        // The competitive scores are designed to award 1 to the absolute best outcome (sole first place) and that
        // total number to the absolute worst outcome (sole last place with every other player tied for first).
        final var uniqueOutcomes = 1 << (PLAYER_COUNT - 1);
        final var worstOutcomeScore = uniqueOutcomes + (PLAYER_COUNT - 1) * (uniqueOutcomes / 2);

        return buildPerspectiveGetterSuite(
            Standings::getCompetitiveScore,
            (standings, initializers) -> {
                int previousCompetitive = 0;
                int previousScore = Integer.MAX_VALUE;
                for (var i = 0; i < PLAYER_COUNT; ++i) {
                    final var initializer = initializers.get(i);
                    final var player = initializer.key();
                    final var score = initializer.value();

                    final var competitive = standings.getCompetitiveScore(player);
                    assertTrue(
                        competitive >= 1,
                        "The competitive score must be greater than or equal to 1."
                    );
                    assertTrue(
                        competitive <= worstOutcomeScore,
                        "The competitive score must be less than or equal to 2^(N-1)+(N-1)*2^(N-2)."
                    );

                    if (score < previousScore) {
                        assertTrue(
                            previousCompetitive < competitive,
                            "Competitive scores must get larger for lower rankings."
                        );
                    } else {
                        assertEquals(
                            previousCompetitive,
                            competitive,
                            "Competitive scores must be equal for equal rankings."
                        );
                    }
                    previousCompetitive = competitive;
                    previousScore = score;
                }
            }
        );
    }

    @TestFactory
    public Stream<DynamicTest> getRankingTest() {
        return buildPerspectiveGetterSuite(
            Standings::getRanking,
            (standings, initializers) -> {
                int previousRanking = 0;
                int previousScore = Integer.MAX_VALUE;
                for (var i = 0; i < PLAYER_COUNT; ++i) {
                    final var initializer = initializers.get(i);
                    final var player = initializer.key();
                    final var score = initializer.value();
                    final var ranking = standings.getRanking(player);

                    if (score < previousScore) {
                        assertEquals(i + 1, ranking);
                    } else {
                        assertEquals(previousRanking, ranking);
                    }
                    previousRanking = ranking;
                    previousScore = score;
                }
            }
        );
    }

    @TestFactory
    public Stream<DynamicTest> getScoreTest() {
        return buildPerspectiveGetterSuite(
            Standings::getRanking,
            (standings, initializers) -> {
                for (var i = 0; i < PLAYER_COUNT; ++i) {
                    final var initializer = initializers.get(i);
                    final var player = initializer.key();
                    final var expected = initializer.value();

                    final var actual = standings.getScore(player);
                    assertEquals(expected, actual);
                }
            }
        );
    }

    @Nested
    public class CompareForTest {
        @Test
        public void mustRejectNullPlayer() {
            final var lhs = generateRandomStandings();
            final var rhs = generateRandomStandings();
            assertThrows(
                NullPointerException.class,
                () -> lhs.compareFor(rhs, null)
            );
        }

        @Test
        public void mustRejectPlayerThatIsNotParticipating() {
            final var player = selectRandomPlayer();
            final var lhs = generateStandingsWithoutPassedPlayer(player);
            final var rhs = generateStandingsWithoutPassedPlayer(player);
            assertThrows(
                InvalidPlayerException.class,
                () -> lhs.compareFor(rhs, player)
            );
        }

        @Test
        public void mustBeGreaterThanNullStandings() {
            final var player = selectRandomPlayer();
            final var standings = generateRandomStandings();
            final var actual = standings.compareFor(null, player);
            assertTrue(
                actual > 0,
                "A Standings object must always be greater than null."
            );
        }

        @Test
        public void mustRejectRhsThatDoesNotHaveTheSameMaximizeScoreSetting() {
            final List<Initializer<FooPlayer, Integer>> initializers = Lists.newArrayList();
            for (final var player : PLAYERS) {
                final var score = generateRandomScore();
                final var initializer = Initializer.of(player, score);
                initializers.add(initializer);
            }
            final var lhs = new Standings<>(
                true,
                (a, b) -> a - b,
                initializers
            );
            final var rhs = new Standings<>(
                false,
                (a, b) -> a - b,
                initializers
            );

            final var player = selectRandomPlayer();
            assertAll(
                () -> assertThrows(
                    MismatchedPlaythroughException.class,
                    () -> lhs.compareFor(rhs, player)
                ),
                () -> assertThrows(
                    MismatchedPlaythroughException.class,
                    () -> rhs.compareFor(lhs, player)
                )
            );
        }

        @Test
        public void mustRejectRhsWithDifferentParticipants() {
            final var maximizeScore = generateRandomBoolean();
            final var lhs = generateRandomStandings(maximizeScore);
            final var lhsParticipants = lhs.getParticipants();

            final var missing = selectRandomPlayer();
            final var rhs = generateStandingsWithoutPassedPlayer(missing, maximizeScore);
            final var rhsParticipants = rhs.getParticipants();

            final var player = getCommon(lhsParticipants, rhsParticipants);
            assertAll(
                () -> assertThrows(
                    MismatchedPlaythroughException.class,
                    () -> lhs.compareFor(rhs, player)
                ),
                () -> assertThrows(
                    MismatchedPlaythroughException.class,
                    () -> rhs.compareFor(lhs, player)
                )
            );
        }

        @Test
        public void mustBeGreaterIfPlayerCompetedBetter() {
            for (var i = 0; i < 1000; ++i) {
                final var maximizeScore = generateRandomBoolean();
                final var player = selectRandomPlayer();
                final var lhs = generateRandomStandings(maximizeScore);
                final var lhsCompetitiveScore = lhs.getCompetitiveScore(player);

                Standings<FooPlayer, Integer> rhs;
                int rhsCompetitiveScore;
                do {
                    rhs = generateRandomStandings(maximizeScore);
                    rhsCompetitiveScore = rhs.getCompetitiveScore(player);
                } while (lhsCompetitiveScore == rhsCompetitiveScore);

                final var comparison = lhs.compareFor(rhs, player);
                if (lhsCompetitiveScore < rhsCompetitiveScore) {
                    assertTrue(
                        comparison > 0,
                        "The comparison value must be greater than 0 if the lhs has a better performance."
                    );
                } else {
                    assertTrue(
                        comparison < 0,
                        "The comparison value must be less than 0 if the lhs has a worse performance."
                    );
                }
            }
        }

        @Test
        public void mustConsiderOnlyCompetitiveScoresWhenMaximizeScoresIsFalse() {
            final var lhs = new Standings<>(
                false,
                (a, b) -> a - b,
                Initializer.of(FooPlayer.FIRST, 1),
                Initializer.of(FooPlayer.SECOND, 0)
            );
            final var rhs = new Standings<>(
                false,
                (a, b) -> a - b,
                Initializer.of(FooPlayer.FIRST, 1000),
                Initializer.of(FooPlayer.SECOND, 0)
            );
            for (final var player : lhs.getParticipants()) {
                final var comparison = lhs.compareFor(rhs, player);
                assertEquals(0, comparison);
            }
        }

        @Test
        public void mustTieBreakWithLeadFirstWhenEquallyCompetitiveAndMaximizeScoreIsTrue() {
            final var lhs = new Standings<>(
                true,
                (a, b) -> a - b,
                Initializer.of(FooPlayer.FIRST, 3),
                Initializer.of(FooPlayer.SECOND, 1)
            );
            final var rhs = new Standings<>(
                true,
                (a, b) -> a - b,
                Initializer.of(FooPlayer.FIRST, 1000),
                Initializer.of(FooPlayer.SECOND, 999)
            );
            for (final var player : lhs.getParticipants()) {
                final var comparison = lhs.compareFor(rhs, player);
                boolean result;
                if (FooPlayer.FIRST == player) {
                    result = comparison > 0;
                } else {
                    result = comparison < 0;
                }

                assertTrue(
                    result,
                    "Standings for games that maximize score must prefer getting as good a lead as possible."
                );
            }
        }

        @Test
        public void mustTieBreakWithScoreSecondWhenOtherChecksAreEqualAndMaximizeScoreIsTrue() {
            final var lhs = new Standings<>(
                true,
                (a, b) -> a - b,
                Initializer.of(FooPlayer.FIRST, 1000),
                Initializer.of(FooPlayer.SECOND, 999)
            );
            final var rhs = new Standings<>(
                true,
                (a, b) -> a - b,
                Initializer.of(FooPlayer.FIRST, 999),
                Initializer.of(FooPlayer.SECOND, 998)
            );
            for (final var player : lhs.getParticipants()) {
                final var comparison = lhs.compareFor(rhs, player);

                assertTrue(
                    comparison > 0,
                    "Standings for games that maximize score must prefer getting as good a score as possible when " +
                        "all other checks are equivalent."
                );
            }
        }

        @Test
        public void mustAlwaysGenerateReflexiveScores() {
            for (var i = 0; i < 1000; ++i) {
                final var maximizeScore = generateRandomBoolean();
                final var lhs = generateRandomStandings(maximizeScore);
                final var rhs = generateRandomStandings(maximizeScore);
                for (final var player : PLAYERS) {
                    final var comparisonFromLhs = lhs.compareFor(rhs, player);
                    final var comparisonFromRhs = rhs.compareFor(lhs, player);
                    assertEquals(0, comparisonFromLhs + comparisonFromRhs);
                }
            }
        }

        @Test
        public void mustPreferBetterCompetitiveScoresWhenNotMaximizingScore() {
            final var allStandings = generateComprehensiveStandings(false);
            final var count = allStandings.size();

            for (final var player : PLAYERS) {
                allStandings.sort((lhs, rhs) -> -lhs.compareFor(rhs, player));

                Standings<FooPlayer, Integer> previous = allStandings.get(0);
                var previousRanking = previous.getRanking(player);
                var previousTrail = getRankingDifferences(previous, player);

                Standings<FooPlayer, Integer> current = null;
                String format = null;
                for (var i = 1; format == null && i < count; ++i) {
                    current = allStandings.get(i);
                    final var currentRanking = current.getRanking(player);
                    final var currentTrail = getRankingDifferences(current, player);
                    if (currentRanking < previousRanking) {
                        format = "%1$s was sorted as being non-dominated by %2$s, but %3$s has a better ranking in " +
                            "the second.";
                        break;
                    } else if (currentRanking == previousRanking) {
                        boolean dominates = false;
                        for (var j = 0; !dominates && format == null && j < PLAYER_COUNT; ++j) {
                            final var previousDelta = previousTrail.get(j);
                            final var currentDelta = currentTrail.get(j);
                            if (previousDelta > 0 && currentDelta > 0) {
                                break;
                            } else if (previousDelta < currentDelta) {
                                format = "%1$s was sorted as being non-dominated by %2$s, but %3$s has a more " +
                                    "competitive performance in the second.";
                            } else if (previousDelta > currentDelta) {
                                dominates = true;
                            }
                        }

                        if (format == null && !dominates) {
                            assertEquals(
                                0,
                                previous.compareFor(current, player)
                            );
                            assertEquals(
                                0,
                                current.compareFor(previous, player)
                            );
                        }
                    }

                    if (format == null) {
                        previous = current;
                        previousRanking = currentRanking;
                        previousTrail = currentTrail;
                    }
                }

                if (format != null) {
                    final var message = String.format(
                        format,
                        previous.represent(player),
                        current.represent(player),
                        player
                    );
                    fail(message);
                }
            }
        }

        private List<Integer> getRankingDifferences(Standings<FooPlayer, Integer> standings, FooPlayer player) {
            final var ranking = standings.getRanking(player);
            return standings.getParticipants()
                .stream()
                .map(participant -> standings.getRanking(participant) - ranking)
                .sorted()
                .toList();
        }

        @Test
        public void mustConsiderScoresAsTieBreakersWhenMaximizingScore() {
            final var allStandings = generateComprehensiveStandings(true);
            final var count = allStandings.size();

            for (final var player : PLAYERS) {
                allStandings.sort((lhs, rhs) -> -lhs.compareFor(rhs, player));

                Standings<FooPlayer, Integer> previous = allStandings.get(0);
                var previousRanking = previous.getRanking(player);
                var previousTrail = getRankingDifferences(previous, player);
                var previousLeads = getLeads(previous, player);
                var previousScore = previous.getScore(player);

                Standings<FooPlayer, Integer> current = null;
                String format = null;
                for (var i = 1; format == null && i < count; ++i) {
                    current = allStandings.get(i);
                    final var currentRanking = current.getRanking(player);
                    final var currentTrail = getRankingDifferences(current, player);
                    final var currentLeads = getLeads(current, player);
                    final var currentScore = current.getScore(player);
                    if (currentRanking < previousRanking) {
                        format = "%1$s was sorted as being non-dominated by %2$s, but %3$s has a better ranking in " +
                            "the second.";
                        break;
                    } else if (currentRanking == previousRanking) {
                        boolean dominates = false;
                        for (var j = 0; !dominates && format == null && j < PLAYER_COUNT; ++j) {
                            final var previousDelta = previousTrail.get(j);
                            final var currentDelta = currentTrail.get(j);
                            if (previousDelta > 0 && currentDelta > 0) {
                                break;
                            } else if (previousDelta < currentDelta) {
                                format = "%1$s was sorted as being non-dominated by %2$s, but %3$s has a more " +
                                    "competitive performance in the second.";
                            } else if (previousDelta > currentDelta) {
                                dominates = true;
                            }
                        }

                        if (format == null && !dominates) {
                            for (var j = 0; !dominates && format == null && j < PLAYER_COUNT; ++j) {
                                final var previousLead = previousLeads.get(j);
                                final var currentLead = currentLeads.get(j);
                                if (previousLead < currentLead) {
                                    format = "%1$s was sorted as being non-dominated by %2$s, but %3$s has a better " +
                                        "lead in the second.";
                                } else if (previousLead > currentLead) {
                                    dominates = true;
                                }
                            }
                        }

                        if (format == null && !dominates && previousScore < currentScore) {
                            format = "%1$s was sorted as being non-dominated by %2$s, but %3$s has a better score in " +
                                "the second.";
                        }
                    }

                    if (format == null) {
                        previous = current;
                        previousRanking = currentRanking;
                        previousTrail = currentTrail;
                        previousLeads = currentLeads;
                        previousScore = currentScore;
                    }
                }

                if (format != null) {
                    final var message = String.format(
                        format,
                        previous.represent(player),
                        current.represent(player),
                        player
                    );
                    fail(message);
                }
            }
        }

        private List<Integer> getLeads(Standings<FooPlayer, Integer> standings, FooPlayer player) {
            final var score = standings.getScore(player);
            return standings.getParticipants()
                .stream()
                .map(participant -> score - standings.getScore(participant))
                .sorted()
                .toList();
        }
    }

    private <T> Stream<DynamicTest> buildPerspectiveGetterSuite(
        BiFunction<Standings<FooPlayer, Integer>, FooPlayer, T> perspectiveGetter,
        BiConsumer<Standings<FooPlayer, Integer>, List<Initializer<FooPlayer, Integer>>> successTest
    ) {
        return Stream.of(
            DynamicTest.dynamicTest(
                "must reject null Player",
                () -> {
                    final var standings = generateRandomStandings();
                    assertThrows(
                        NullPointerException.class,
                        () -> perspectiveGetter.apply(standings, null)
                    );
                }
            ),
            DynamicTest.dynamicTest(
                "must reject Player that is not participating",
                () -> {
                    final var player = selectRandomPlayer();
                    final var standings = generateStandingsWithoutPassedPlayer(player);
                    assertThrows(
                        InvalidPlayerException.class,
                        () -> perspectiveGetter.apply(standings, player)
                    );
                }
            ),
            DynamicTest.dynamicTest(
                "must return requested value for participating Player",
                () -> {
                    for (var i = 0; i < 1000; ++i) {
                        final List<Initializer<FooPlayer, Integer>> initializers = Lists.newArrayList();
                        for (var player : PLAYERS) {
                            final var score = generateRandomScore();
                            final var initializer = Initializer.of(player, score);
                            initializers.add(initializer);
                        }

                        final var standings = new Standings<>(
                            generateRandomBoolean(),
                            (lhs, rhs) -> lhs - rhs,
                            initializers
                        );

                        initializers.sort(
                            Comparator.comparing(
                                Initializer::value,
                                Comparator.reverseOrder()
                            )
                        );
                        successTest.accept(standings, initializers);
                    }
                }
            )
        );
    }

    // Developer's Note: (J. Craig, 2022-07-21)
    // This is incredibly lazy.  I don't care so much about rigorously testing toString() or represent() because they
    // primarily exist for logging.  I _should_ write better tests here.  I want to move on to writing the rest of the
    // library.  It has taken me a couple weeks of massaging Stangings to finally get it where I want it.
    @Nested
    public class ToStringTest {
        @Test
        public void mustWorkWhenNotMaximizingScore() {
            final var standings = generateRandomStandings(false);
            final var representation = standings.toString();
            assertTrue(StringUtils.isNotBlank(representation));
        }

        @Test
        public void mustWorkWhenMaximizingScore() {
            final var standings = generateRandomStandings(true);
            final var representation = standings.toString();
            assertTrue(StringUtils.isNotBlank(representation));
        }
    }

    @Nested
    public class RepresentTest {
        @Test
        public void mustRejectNullPlayer() {
            final var standings = generateRandomStandings();
            assertThrows(
                NullPointerException.class,
                () -> standings.represent(null)
            );
        }

        @Test
        public void mustRejectPlayerThatIsNotParticipating() {
            final var omitted = selectRandomPlayer();
            final var standings = generateStandingsWithoutPassedPlayer(omitted);
            assertThrows(
                InvalidPlayerException.class,
                () -> standings.represent(omitted)
            );
        }

        @Test
        public void mustWorkWhenNotMaximizingScore() {
            final var standings = generateRandomStandings(false);
            for (final var player : PLAYERS) {
                final var representation = standings.represent(player);
                assertTrue(StringUtils.isNotBlank(representation));
            }
        }

        @Test
        public void mustWorkWhenMaximizingScore() {
            final var standings = generateRandomStandings(false);
            for (final var player : PLAYERS) {
                final var representation = standings.represent(player);
                assertTrue(StringUtils.isNotBlank(representation));
            }
        }
    }

    private Standings<FooPlayer, Integer> generateStandingsWithoutPassedPlayer(FooPlayer player) {
        final var participants = Sets.newHashSet(PLAYERS);
        participants.remove(player);

        final List<Initializer<FooPlayer, Integer>> initializers = Lists.newArrayList();
        for (final var participant : participants) {
            final var initializer = Initializer.of(participant, generateRandomScore());
            initializers.add(initializer);
        }

        return new Standings<>(
            generateRandomBoolean(),
            (lhs, rhs) -> lhs - rhs,
            initializers
        );
    }

    private Standings<FooPlayer, Integer> generateStandingsWithoutPassedPlayer(FooPlayer player, boolean maximizeScore) {
        final var participants = Sets.newHashSet(PLAYERS);
        participants.remove(player);

        final List<Initializer<FooPlayer, Integer>> initializers = Lists.newArrayList();
        for (final var participant : participants) {
            final var initializer = Initializer.of(participant, generateRandomScore());
            initializers.add(initializer);
        }

        return new Standings<>(
            maximizeScore,
            (lhs, rhs) -> lhs - rhs,
            initializers
        );
    }

    private <T> T getCommon(Set<T> a, Set<T> b) {
        final var intersection = a.stream()
            .filter(b::contains)
            .toList();
        final var count = intersection.size();
        if (count == 0) {
            throw new IllegalArgumentException();
        }

        final var random = ThreadLocalRandom.current();
        final var index = random.nextInt(count);
        return intersection.get(index);
    }

    private List<Standings<FooPlayer, Integer>> generateComprehensiveStandings(boolean maximizeScore) {
        final List<Standings<FooPlayer, Integer>> allStandings = Lists.newArrayList();
        final var scores = new int[] { 0, 0, 0, 0, 0 };
        final BiFunction<Integer, Integer, Integer> subtract = (lhs, rhs) -> lhs - rhs;
        while (scores[0] < 3) {
            final List<Initializer<FooPlayer, Integer>> initializers = Lists.newArrayList();
            for (var i = 0; i < PLAYER_COUNT; ++i) {
                final var player = PLAYERS.get(i);
                final var score = scores[i];
                final var initializer = Initializer.of(player, score);
                initializers.add(initializer);
            }

            final var standings = new Standings<>(maximizeScore, subtract, initializers);
            allStandings.add(standings);

            boolean mustContinue = true;
            for (int j = 4; mustContinue; --j) {
                if (j > 0 && scores[j] == 3) {
                    scores[j] = 0;
                } else {
                    ++scores[j];
                    mustContinue = false;
                }
            }
        }

        return allStandings;
    }
}
