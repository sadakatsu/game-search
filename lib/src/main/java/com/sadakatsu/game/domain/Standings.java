package com.sadakatsu.game.domain;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.sadakatsu.game.exceptions.InvalidPlayerException;
import com.sadakatsu.game.exceptions.MismatchedPlaythroughException;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import javax.annotation.CheckForNull;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Standings<PLAYER extends Player, SCORE extends Comparable<SCORE>> {
    private static class Distribution {
        private final int count;
        private final List<Integer> distribution;
        private final Map<Integer, Integer> competitionScores;

        Distribution(List<Integer> distribution) {
            this.count = distribution.size();
            this.distribution = distribution.stream()
                .sorted()
                .toList();
            this.competitionScores = Maps.newHashMap();
        }

        int getCompetitionScoreFor(int ranking) {
            return competitionScores.getOrDefault(ranking, -1);
        }

        void setCompetitionScoreFor(int ranking, int competitionScore) {
            competitionScores.put(ranking, competitionScore);
        }

        int compareFor(@NotNull Distribution rhs, int ranking) {
            int comparison = 0;

            if (!hasRanking(ranking)) {
                comparison = rhs.hasRanking(ranking) ? -1 : 0;
            } else if (!rhs.hasRanking(ranking)) {
                comparison = 1;
            } else {
                boolean stillCare = true;
                // Developer's Note: (J. Craig, 2022-07-19)
                // I originally had this written in my preferred way (short-circuits, then bounds check):
                //  comparison == 0 && stillCare && i < count
                // JaCoCo showed that the loop always finishes before "i < count" was false.
                // So, um, being a bit courageous here  >_<
                for (int i = 0; comparison == 0 && stillCare; ++i) {
                    final var lhsValue = distribution.get(i);
                    final var rhsValue = rhs.distribution.get(i);
                    if (lhsValue > ranking && rhsValue > ranking) {
                        stillCare = false;
                    } else {
                        comparison = lhsValue - rhsValue;
                    }
                }
            }

            return comparison;
        }

        public boolean hasRanking(int ranking) {
            return distribution.get(ranking - 1) == ranking;
        }
    }

    record Initializer<P extends Player, S extends Comparable<S>>(
        @NonNull P key,
        @NonNull S value
    ) implements Map.Entry<P, S> {
        public static <P extends Player, S extends Comparable<S>> Initializer<P, S> of(
            @NonNull P player,
            @NonNull S score
        ) {
            return new Initializer<>(player, score);
        }

        @Override
        public P getKey() {
            return key;
        }

        @Override
        public S getValue() {
            return value;
        }

        @Override
        public S setValue(S value) {
            throw new UnsupportedOperationException("StandingsInitializers are immutable.");
        }
    }

    private static final Map<Integer, Map<List<Integer>, Distribution>> CACHE = Maps.newConcurrentMap();

    private static synchronized Map<List<Integer>, Distribution> getCompetitionCacheFor(int playerCount) {
        return CACHE.computeIfAbsent(
            playerCount,
            Standings::buildCacheFor
        );
    }

    private static Map<List<Integer>, Distribution> buildCacheFor(int playerCount) {
        final var distributions = getAllDistributions(playerCount);
        final var count = distributions.size();

        var baseCompetitionScore = 1;
        for (var ranking = 1; ranking <= playerCount; ++ranking) {
            final var rankingCopy = ranking;
            distributions.sort((lhs, rhs) -> rhs.compareFor(lhs, rankingCopy));

            Distribution previous = null;
            int i;
            int offset = 0;
            for (i = 0; i < count; ++i) {
                final var distribution = distributions.get(i);
                if (!distribution.hasRanking(ranking)) {
                    break;
                } else if (previous != null && previous.compareFor(distribution, ranking) > 0) {
                    offset = i;
                }

                distribution.setCompetitionScoreFor(ranking, baseCompetitionScore + offset);
                previous = distribution;
            }

            baseCompetitionScore += i;
        }

        return distributions.stream()
            .collect(Collectors.toMap(x -> x.distribution, Function.identity()));
    }

    private static List<Distribution> getAllDistributions(int playerCount) {
        // Developer's Note: (J. Craig, 2022-07-18)
        // Getting all ranking distributions for a player count is pretty simple.  There must always be at least one
        // player in first place.  Every other placement is either tied with the previous placement or worse.  We do not
        // need to know which Player earned which place, as we are trying to determine how to assign outcomes to each
        // Distribution/ranking combination to optimize game searching.
        //
        // That means that, for N players, there are 2^(N-1) possible Distributions.
        final var distributionCount = 1 << (playerCount - 1);
        final List<Distribution> distributions = Lists.newArrayListWithExpectedSize(playerCount);

        final var worse = new boolean[playerCount - 1];
        for (var i = 0; i < distributionCount; ++i) {
            var ranking = 1;
            final List<Integer> rankings = Lists.newArrayListWithExpectedSize(playerCount);
            rankings.add(1);
            for (var j = 1; j < playerCount; ++j) {
                if (worse[j - 1]) {
                    ranking = j + 1;
                }
                rankings.add(ranking);
            }

            final var distribution = new Distribution(rankings);
            distributions.add(distribution);

            if (i + 1 < distributionCount) {
                boolean working = true;
                for (var j = playerCount - 2; working; --j) {
                    if (worse[j]) {
                        worse[j] = false;
                    } else {
                        worse[j] = true;
                        working = false;
                    }
                }
            }
        }

        return distributions;
    }

    private static <
        P extends Player,
        S extends Comparable<S>
    > Iterable<Initializer<P, S>> convertToListOrError(@CheckForNull Initializer<P, S>[] entries) {
        if (entries == null) {
            throw new NullPointerException("The entries array may not be null.");
        } else if (entries.length == 0) {
            throw new IllegalArgumentException("You must provide entries to set the Player Scores.");
        }
        return Arrays.asList(entries);
    }

    @RequiredArgsConstructor
    private class Performance {
        final PLAYER player;

        @Getter
        final SCORE score;

        @Getter
        int ranking;

        @Getter
        int competitiveScore;

        @Override
        public String toString() {
            return String.format(
                "{ %s : %d (%d, %s) }",
                player,
                ranking,
                competitiveScore,
                score
            );
        }
    }

    private final boolean maximizeScore;
    private final BiFunction<SCORE, SCORE, SCORE> subtract;
    private final Map<PLAYER, Performance> performances;
    private PLAYER first;

    @Getter
    private final Set<PLAYER> participants;

    private final int participantCount;

    @SafeVarargs
    public Standings(
        boolean maximizeScore,
        BiFunction<SCORE, SCORE, SCORE> subtract,
        Initializer<PLAYER, SCORE>...entries
    ) {
        this(
            maximizeScore,
            subtract,
            convertToListOrError(entries)
        );
    }

    public Standings(
        boolean maximizeScore,
        @NonNull BiFunction<SCORE, SCORE, SCORE> subtract,
        @NonNull Iterable<Initializer<PLAYER, SCORE>> entries
    ) {
        this.maximizeScore = maximizeScore;
        this.subtract = subtract;

        this.performances = recordPlayerScores(entries);
        this.participants = cacheParticipants();
        this.participantCount = participants.size();
        rankPerformances();
        scoreCompetition();
    }

    private Map<PLAYER, Performance> recordPlayerScores(Iterable<Initializer<PLAYER, SCORE>> entries) {
        var first = true;
        final Map<PLAYER, Performance> scratch = Maps.newHashMap();
        for (final var entry : entries) {
            if (entry == null) {
                throw new IllegalArgumentException("None of the entries may be null.");
            }
            final var player = entry.getKey();
            if (scratch.containsKey(player)) {
                throw new IllegalArgumentException("None of the Players may be repeated.");
            } else if (first) {
                first = false;
                this.first = player;
            }

            final var score = entry.getValue();
            final var performance = new Performance(player, score);
            scratch.put(player, performance);
        }

        if (maximizeScore && scratch.isEmpty()) {
            throw new IllegalArgumentException(
                "A game where one attempts to maximize one's score needs at least one Player."
            );
        } else if (!maximizeScore && scratch.size() < 2) {
            throw new IllegalArgumentException(
                "A game where one does not attempt to maximize one's score needs at least two Players."
            );
        }

        return scratch;
    }

    private Set<PLAYER> cacheParticipants() {
        return Collections.unmodifiableSet(performances.keySet());
    }

    private void rankPerformances() {
        final List<Performance> sorted = performances.values()
            .stream()
            .sorted(
                Comparator.comparing(Performance::getScore)
                    .reversed()
            )
            .toList();

        var first = sorted.get(0);
        var previousScore = first.score;
        var ranking = 1;
        first.ranking = 1;
        for (int i = 1, count = sorted.size(); i < count; ++i) {
            final var current = sorted.get(i);
            final var currentScore = current.score;
            if (previousScore.compareTo(currentScore) > 0) {
                ranking = i + 1;
            }
            current.ranking = ranking;
            previousScore = currentScore;
        }
    }

    private void scoreCompetition() {
        final var competitionCache = getCompetitionCacheFor(participantCount);

        final var key = getSortedRankings();
        final var distribution = competitionCache.get(key);
        for (final var performance : performances.values()) {
            performance.competitiveScore = distribution.getCompetitionScoreFor(performance.ranking);
        }
    }

    private List<Integer> getSortedRankings() {
        return performances.values()
            .stream()
            .map(Performance::getRanking)
            .sorted()
            .toList();
    }

    public int getCompetitiveScore(PLAYER player) {
        return getPerformanceOrError(player).getCompetitiveScore();
    }

    private Performance getPerformanceOrError(@NonNull PLAYER player) {
        if (!participants.contains(player)) {
            throw new InvalidPlayerException();
        }
        return performances.get(player);
    }

    public int getRanking(PLAYER player) {
        return getPerformanceOrError(player).getRanking();
    }

    public SCORE getScore(PLAYER player) {
        return getPerformanceOrError(player).getScore();
    }

    public int compareFor(
        @CheckForNull Standings<PLAYER, SCORE> rhs,
        @CheckForNull PLAYER player
    ) {
        int comparison;

        final var lhsPerformance = getPerformanceOrError(player);
        if (rhs == null) {
            comparison = 1;
        } else if (maximizeScore != rhs.maximizeScore || !participants.equals(rhs.participants)) {
            throw new MismatchedPlaythroughException();
        } else {
            final var rhsPerformance = rhs.performances.get(player);
            comparison = rhsPerformance.competitiveScore - lhsPerformance.competitiveScore;

            if (comparison == 0 && maximizeScore) {
                comparison = compareLeads(rhs, lhsPerformance, rhsPerformance);

                if (comparison == 0) {
                    comparison = compareScores(lhsPerformance, rhsPerformance);
                }
            }
        }

        return comparison;
    }

    private int compareLeads(Standings<PLAYER, SCORE> rhs, Performance lhsPerformance, Performance rhsPerformance) {
        int comparison = 0;

        final var lhsLeads = getLeads(lhsPerformance);
        final var rhsLeads = rhs.getLeads(rhsPerformance);
        for (int i = 0; comparison == 0 && i < participantCount; ++i) {
            final var lhsLead = lhsLeads.get(i);
            final var rhsLead = rhsLeads.get(i);
            comparison = lhsLead.compareTo(rhsLead);
        }

        return comparison;
    }

    private List<SCORE> getLeads(Performance reference) {
        final var score = reference.score;
        return performances.values()
            .stream()
            .map(performance -> subtract.apply(score, performance.score))
            .sorted()
            .collect(Collectors.toList());
    }

    private int compareScores(Performance lhsPerformance, Performance rhsPerformance) {
        final var lhsScore = lhsPerformance.score;
        final var rhsScore = rhsPerformance.score;
        return lhsScore.compareTo(rhsScore);
    }

    @Override
    public String toString() {
        return represent(first);
    }

    public String represent(PLAYER player) {
        final var performance = getPerformanceOrError(player);

        final var builder = new StringBuilder();

        final var sorted = performances.values()
            .stream()
            .sorted(Comparator.comparing(Performance::getScore, Comparator.reverseOrder()))
            .toList();

        final var ranking = performance.ranking;
        final var rankingDeltas = sorted.stream()
            .map(p -> p.getRanking() - ranking)
            .toList();

        builder.append(rankingDeltas);
        builder.append(" : ");

        if (maximizeScore) {
            final var score = performance.score;
            final var leads = sorted.stream()
                .map(p -> subtract.apply(score, p.getScore()))
                .toList();

            builder.append(leads);
            builder.append(" : ");
        }

        builder.append(sorted);

        return builder.toString();
    }
}
