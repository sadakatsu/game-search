package com.sadakatsu.game.domain;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.sadakatsu.game.exceptions.InvalidPlayerException;
import com.sadakatsu.game.exceptions.MismatchedPlaythroughException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class StandingsTest {
    private enum FooPlayer implements Player {
        FIRST,
        SECOND,
        THIRD,
        FOURTH
    }

    @Getter
    @EqualsAndHashCode
    private static class Standing {
        private final FooPlayer player;
        private final int score;

        @Setter
        private int ranking;

        public Standing(@NonNull FooPlayer player, int score) {
            this.player = player;
            this.ranking = 1;
            this.score = score;
        }

        @Override
        public String toString() {
            return String.format("%s %d (%d)", player, ranking, score);
        }
    }

    @EqualsAndHashCode
    private static class FooStandings implements Standings<FooPlayer, Integer, FooStandings> {
        private final Map<FooPlayer, Standing> standings;
        private final FooPlayer soleLeader;
        private final Set<FooPlayer> participants;

        public FooStandings(int firstScore, int secondScore, int thirdScore, int fourthScore) {
            standings = Maps.newLinkedHashMapWithExpectedSize(4);

            if (firstScore >= 0) {
                final var first = new Standing(FooPlayer.FIRST, firstScore);
                standings.put(FooPlayer.FIRST, first);
            }

            if (secondScore >= 0) {
                final var second = new Standing(FooPlayer.SECOND, secondScore);
                standings.put(FooPlayer.SECOND, second);
            }

            if (thirdScore >= 0) {
                final var third = new Standing(FooPlayer.THIRD, thirdScore);
                standings.put(FooPlayer.THIRD, third);
            }

            if (fourthScore >= 0) {
                final var fourth = new Standing(FooPlayer.FOURTH, fourthScore);
                standings.put(FooPlayer.FOURTH, fourth);
            }

            if (standings.isEmpty()) {
                throw new IllegalArgumentException();
            }
            participants = Collections.unmodifiableSet(standings.keySet());

            final var standingsList = Lists.newArrayList(standings.values());
            standingsList.sort(
                Comparator.comparing(Standing::getScore)
                    .reversed()
            );

            var previous = standingsList.get(0);
            var ranking = 1;
            var soleLeaderCandidate = previous.getPlayer();
            for (int i = 0, count = participants.size(); i < count; ++i) {
                final var current = standingsList.get(i);
                if (previous.getScore() > current.getScore()) {
                    ranking = i + 1;
                } else if (i == 1) {
                    soleLeaderCandidate = null;
                }
                current.setRanking(ranking);
                previous = current;
            }

            soleLeader = soleLeaderCandidate;
        }

        @Override
        @NonNull
        public Set<FooPlayer> getParticipants() {
            return participants;
        }

        @Override
        public boolean isSoleLeader(@NonNull FooPlayer player) throws InvalidPlayerException {
            return Objects.equals(soleLeader, player);
        }

        @Override
        public int getRanking(@NonNull FooPlayer player) throws InvalidPlayerException {
            return standings.get(player).getRanking();
        }

        @Override
        @NonNull
        public Integer getScore(@NonNull FooPlayer player) throws InvalidPlayerException {
            return standings.get(player).getScore();
        }

        @Override
        public Integer subtract(@NonNull Integer minuend, @NonNull Integer subtrahend) {
            return minuend - subtrahend;
        }

        @Override
        public String toString() {
            return represent(FooPlayer.FIRST);
        }

        public String represent(@NonNull FooPlayer player) {
            final var builder = new StringBuilder();

            final var playerScore = standings.get(player).getScore();
            final var deltas = standings.values()
                .stream()
                .sorted(Comparator.comparing(Standing::getRanking))
                .map(standing -> playerScore - standing.getScore())
                .map(Object::toString)
                .collect(Collectors.joining(", "));
            builder.append(deltas);

            builder.append(" : ");

            final var sortedStandings = standings.values()
                .stream()
                .sorted(
                    Comparator.comparing(Standing::getRanking)
                        .thenComparing(Standing::getPlayer)
                )
                .map(Standing::toString)
                .collect(Collectors.joining(", "));
            builder.append(sortedStandings);

            return builder.toString();
        }

        public List<Integer> getDifferences(@NonNull FooPlayer player) {
            final var playerScore = standings.get(player).getScore();
            return standings.values()
                .stream()
                .sorted(Comparator.comparing(Standing::getRanking))
                .map(standing -> playerScore - standing.getScore())
                .toList();
        }
    }

    @Nested
    public class CompareAgainstTest {
        @Test
        public void mustRejectNullPlayer() {
            final var lhs = generateRandomStandings();
            final var rhs = generateRandomStandings();
            assertThrows(
                NullPointerException.class,
                () -> lhs.compareAgainst(rhs, null)
            );
        }

        @Test
        public void mustRejectPlayerThatIsNotAParticipant() {
            final var participants = Sets.newHashSet(allPlayersSet);
            final var notParticipating = selectRandomPlayer();
            participants.remove(notParticipating);

            final var lhs = generateRandomStandingsWithPlayers(participants);
            final var rhs = generateRandomStandingsWithPlayers(participants);
            assertThrows(
                InvalidPlayerException.class,
                () -> lhs.compareAgainst(rhs, notParticipating)
            );
        }

        @Test
        public void mustAlwaysDominateANullStandings() {
            final var lhs = generateRandomStandings();
            for (final var player : allPlayersSet) {
                final var actual = lhs.compareAgainst(null, player);
                assertTrue(actual > 0);
            }
        }

        @Test
        public void mustRejectStandingsWithMismatchedParticipants() {
            final var lhs = generateRandomStandings();
            final var rhs = generateRandomStandingsWithRandomSubsetOfPlayers();
            final var player = selectRandomPlayer();
            assertThrows(
                MismatchedPlaythroughException.class,
                () -> lhs.compareAgainst(rhs, player)
            );
        }

        @Test
        public void mustCompareAgainstEachOtherSuchThatNoItemEverFollowsAnItemThatItDominates() {
            final List<FooStandings> standingsList = Lists.newArrayList();
            final var max = 5;
            for (int first = 0; first < max; ++first) {
                for (int second = 0; second < max; ++second) {
                    for (int third = 0; third < max; ++third) {
                        for (int fourth = 0; fourth < max; ++fourth) {
                            final var standings = new FooStandings(first, second, third, fourth);
                            standingsList.add(standings);
                        }
                    }
                }
            }

            for (final var player : FooPlayer.values()) {
                standingsList.sort((lhs, rhs) -> -lhs.compareAgainst(rhs, player));
                for (int i = 0, count = standingsList.size(); i < count - 1; ++i) {
                    final var lhs = standingsList.get(i);
                    final var lhsRanking = lhs.getRanking(player);
                    final var lhsSoleLeadership = lhs.isSoleLeader(player);
                    final var lhsDifferences = lhs.getDifferences(player);
                    final var lhsScore = lhs.getScore(player);
                    for (int j = i + 1; j < count; ++j) {
                        final var rhs = standingsList.get(j);
                        final var rhsRanking = rhs.getRanking(player);
                        final var rhsSoleLeadership = rhs.isSoleLeader(player);
                        final var rhsDifferences = rhs.getDifferences(player);
                        final var rhsScore = rhs.getScore(player);

                        String format = null;
                        if (lhsRanking > rhsRanking) {
                            format = "standings::compareAgainst() determined that lhs is non-dominated by rhs for " +
                                "player %1$s, but %1$s is better ranked in rhs: %2$s, %3$s";
                        } else if (lhsRanking == rhsRanking) {
                            if (!lhsSoleLeadership && rhsSoleLeadership) {
                                format = "standings::compareAgainst() determined that lhs is non-dominated by rhs " +
                                    "for player %1$s, but %1$s is the sole leader in rhs: %2$s, %3$s";
                            } else if (lhsSoleLeadership == rhsSoleLeadership) {
                                var provenBetter = false;
                                for (var k = 0; k < 4; ++k) {
                                    final var lhsDifference = lhsDifferences.get(k);
                                    final var rhsDifference = rhsDifferences.get(k);
                                    if (lhsDifference < rhsDifference) {
                                        format = "standings::compareAgainst() determined that lhs is non-dominated " +
                                            "by rhs for player %1$s, but rhs has a better difference: %2$s, %3$s";
                                        break;
                                    } else if (lhsDifference > rhsDifference) {
                                        provenBetter = true;
                                        break;
                                    }
                                }

                                if (!provenBetter && format == null && lhsScore < rhsScore) {
                                    format = "standings::compareAgainst() determined that lhs is non-dominated by " +
                                        "rhs for player %1$s, but %1$s scored better in rhs: %2$s, %3$s";
                                }
                            }
                        }

                        if (format != null) {
                            final var message = String.format(
                                format,
                                player,
                                lhs.represent(player),
                                rhs.represent(player)
                            );
                            fail(message);
                        }
                    }
                }
            }
        }
    }

    @Nested
    public class CompareNullableTest {
        @Test
        public void mustRejectNullPlayer() {
            final var lhs = generateRandomStandings();
            final var rhs = generateRandomStandings();
            assertThrows(
                NullPointerException.class,
                () -> Standings.compareNullable(lhs, rhs, null)
            );
        }

        @Test
        public void mustFindNullsMutuallyNonDominated() {
            final var player = selectRandomPlayer();
            final var actual = Standings.compareNullable(null, null, player);
            assertEquals(0, actual);
        }

        @Test
        public void mustFindNullDominatedByNonNull() {
            final var rhs = generateRandomStandings();
            final var player = selectRandomPlayer();
            final var actual = Standings.compareNullable(null, rhs, player);
            assertTrue(actual < 0);
        }

        @Test
        public void mustFindNonNullDominatesNull() {
            final var lhs = generateRandomStandings();
            final var player = selectRandomPlayer();
            final var actual = Standings.compareNullable(lhs, null, player);
            assertTrue(actual > 0);
        }

        @Test
        public void mustDeferToCompareAgainstWhenBothAreNonNull() {
            final var lhs = mock(FooStandings.class);
            final var rhs = mock(FooStandings.class);
            final var player = selectRandomPlayer();
            final var expected = ThreadLocalRandom.current().nextInt(-100, 100);
            when(lhs.compareAgainst(rhs, player))
                .thenReturn(expected);

            final var actual = Standings.compareNullable(lhs, rhs, player);
            assertAll(
                () -> assertEquals(expected, actual),
                () -> verify(lhs).compareAgainst(rhs, player)
            );
        }
    }

    private static final FooPlayer[] allPlayersArray = FooPlayer.values();
    private static final Set<FooPlayer> allPlayersSet = Sets.newHashSet(allPlayersArray);

    private FooStandings generateRandomStandings() {
        return generateRandomStandingsWithPlayers(allPlayersSet);
    }

    private FooStandings generateRandomStandingsWithPlayers(Set<FooPlayer> participants) {
        final var first = generateScoreForPlayer(FooPlayer.FIRST, participants);
        final var second = generateScoreForPlayer(FooPlayer.SECOND, participants);
        final var third = generateScoreForPlayer(FooPlayer.THIRD, participants);
        final var fourth = generateScoreForPlayer(FooPlayer.FOURTH, participants);
        return new FooStandings(first, second, third, fourth);
    }

    private int generateScoreForPlayer(FooPlayer player, Set<FooPlayer> participants) {
        return (
            participants.contains(player) ?
                ThreadLocalRandom.current().nextInt(0, 101) :
                -1
        );
    }

    private FooPlayer selectRandomPlayer() {
        final var random = ThreadLocalRandom.current();
        final var index = random.nextInt(allPlayersArray.length);
        return allPlayersArray[index];
    }

    private FooStandings generateRandomStandingsWithRandomSubsetOfPlayers() {
        final var candidates = Arrays.copyOf(allPlayersArray, allPlayersArray.length);
        final var random = ThreadLocalRandom.current();
        for (var i = 0; i < candidates.length; ++i) {
            final var j = random.nextInt(candidates.length);
            final var swap = candidates[i];
            candidates[i] = candidates[j];
            candidates[j] = swap;
        }
        final var playerCount = random.nextInt(1, candidates.length);
        final Set<FooPlayer> participants = Sets.newHashSet();
        for (var i = 0; i < playerCount; ++i) {
            participants.add(candidates[i]);
        }
        return generateRandomStandingsWithPlayers(participants);
    }
}
