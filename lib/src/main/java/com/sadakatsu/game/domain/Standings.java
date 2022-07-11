package com.sadakatsu.game.domain;

import com.sadakatsu.game.exceptions.InvalidPlayerException;
import com.sadakatsu.game.exceptions.MismatchedPlaythroughException;
import lombok.NonNull;
import org.apache.commons.collections4.CollectionUtils;

import javax.annotation.CheckForNull;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * The Standings class captures either a final or intermediate evaluation of how each Player participating in a game
 * playthrough is performing in comparison to the others.  It performs a pivotal role for the search algorithms, so one
 * implementing it must follow the contracts documented here carefully.
 * </p>
 * <p>
 * Each Standings implementation should be fully immutable.  This will assist with ensuring correctness of any
 * concurrent searches.
 * </p>
 * <p>
 * Each Standings implementation must implement equals() and hashCode() in reference to the participating Players and
 * their scores.  Different Standings instances created with the same inputs must be equal to each other.  These
 * Standings will be stored in Sets by the search algorithms.
 * </p>
 * <p>
 * Each Standings instance must track the Players participating in the game playthrough.  It would be best to calculate
 * an immutable Set of the players once, then reuse it for each Standings instance.
 * </p>
 * <p>
 * Each Standings implementation must track each Player's score.  If the implemented game has points, the Standings must
 * report those points for terminal State Standings instances.  Intermediate State Standings may use any heuristic
 * evaluation the implementer finds useful, but it is recommended that these correlate to the game's point system as
 * closely as possible to avoid problems.
 * </p>
 * <p>
 * Each Standings implementation must track each Player's ranking based upon his score using
 * <a href="https://en.wikipedia.org/wiki/Ranking#Standard_competition_ranking_(%221224%22_ranking)">standard
 * competition ranking</a>.  Player rankings will be queried by every <code>Standings::compareAgainst()</code> call
 * during the search, so the implementer should calculate and cache these rankings during instantiation.
 * </p>
 * <p>
 * Each Standings instance must track whether one Player is the "sole leader", meaning that one Player alone has the
 * most points.  It is recommended to cache this during instantiation, as the search algorithms are likely to check this
 * state many times.
 * </p>
 * <p>
 * Each Standings instance must implement subtract() to get the difference between two SCOREs.  The Standings interface
 * is designed to support any number of Players greater than zero and to support non-scalar scoring systems.  For
 * example, <a href="https://boardgamegeek.com/boardgame/9674/ingenious">Ingenious</a> tracks one score for each of six
 * colors for each Player.  It can also support the idea of teams, allowing for each Player to track both overall team
 * and individual performance scores in one SCORE object.  Unfortunately, this means that the library cannot guarantee
 * that a SCORE is a simple number capable of language-provided subtraction.  Therefore, the implementer must provide a
 * subtraction method that returns another SCORE instance that captures how much better or worse the callee is than the
 * argument.  This difference must be linearly scaled and be universally comparable (i.e., if
 * <code>subtract(a, b).compareTo(subtract(c, d)) == 0</code>, then a is exactly as much better than b as c is better
 * than d).  This may require some careful thought for complex scoring systems.
 * </p>
 * @param <PLAYER> the Player type for this game
 * @param <SCORE> the score type used to capture each Player's score/points; these must have a total ordering and
 *               support subtraction to retrieve a difference
 * @param <STANDINGS> the Standing implementation type against which this Standing implementation can be compared
 *                   against; should almost the same type (e.g., <code>public class ChessStandings implements
 *                   Standings&lt;ChessPlayer, Integer, ChessStandings&gt;</code>)
 */
public interface Standings<
    PLAYER extends Player,
    SCORE extends Comparable<SCORE>,
    STANDINGS extends Standings<PLAYER, SCORE, STANDINGS>
> {
    /**
     * @return the Set of Players that are playing in the Game whose State's Standings are being captured
     */
    @NonNull Set<PLAYER> getParticipants();

    /**
     * <p>
     * "Sole leadership" here means that the passed Player has the highest (and therefore winning) score, and that no
     * other Player is tied with this Player to share the win.
     * </p>
     * <p>
     * This method should be implemented to honor this definition.  This method is used by the search logic.  When
     * searching for a Player who is the sole leader, the algorithms will prefer the shortest proven path among multiple
     * alternatives to the minimax outcome.  This leads to elegant victories and decreasing opportunities for making
     * mistakes.  When searching for other Players, the algorithms will attempt to prolong the game.  This emulates good
     * fighting spirit and increases opportunities to catch up or even come back.
     * </p>
     * <p>
     * If one's game implementation groups Players into teams, then this method must instead check that no other team
     * matches this player's team's score.
     * </p>
     * @param player the Player whose sole leadership is being tested
     * @return true iff the passed Player is the sole leader
     * @throws InvalidPlayerException iff the passed Player is not participating in this game playthrough
     */
    boolean isSoleLeader(@NonNull PLAYER player) throws InvalidPlayerException;

    /**
     * <p>
     * This method queries a Player's ranking against the other Players participating in the game using
     * <a href="https://en.wikipedia.org/wiki/Ranking#Standard_competition_ranking_(%221224%22_ranking)">standard
     * competition ranking</a>.  The winners have rank 1, and the other players have lower ranks based upon how their
     * scores are sorted and tied.  Using this system will facilitate search so that players try to first optimize their
     * ranking, then their sole leadership, and then their score.
     * </p>
     * <p>
     * If one's game implementation groups Players into teams, then this method must return the whole team's ranking,
     * even if individual team members' scores are tracked separately.
     * </p>
     * @param player the Player whose ranking is being queried
     * @return 1 if the Player is in first place, 2 if the Player is in second place, etc.
     * @throws InvalidPlayerException iff the passed Player is not participating in this game playthrough
     */
    int getRanking(@NonNull PLAYER player) throws InvalidPlayerException;

    /**
     * <p>
     * This method returns the passed Player's score based upon whatever system is being used to evaluate how well the
     * Player is playing.  Games with actual scores must report these scores for terminal States' Standings.
     * Intermediate States' Standings or Standings for Games that do not have scores can use whatever scheme the
     * implementer finds improves search performance, though it would still be good to try to reflective the game's
     * actual score system if it exists.
     * </p>
     * <p>
     * Team-based games with overall and individual scoring would likely benefit by using a SCORE type that can capture
     * both values but emphasizes the overall score.  For example, a Player who sacrifices his own score to ensure his
     * team gets a better overall score is playing properly minimax.  Maximizing one's one score is a liberty for a
     * Player who has multiple outcomes that lead to his team having the same overall score.
     * </p>
     * @param player the Player whose score is being queried
     * @return either the Player's actual score based upon search to a terminal node or a heuristic score that is
     * designed to steer search toward more promising results
     * @throws InvalidPlayerException iff the passed Player is not participating in this game playthrough
     */
    @NonNull SCORE getScore(@NonNull PLAYER player) throws InvalidPlayerException;

    /**
     * Alas, Java does not have operator overloading, so I cannot enforce that SCORE has some method to subtract one
     * score from another.  Therefore, Standings implementations must provide this subtraction here to support the
     * default compareAgainst implementation
     * @param minuend the first number in the subtraction operation; the left-hand side
     * @param subtrahend the second number in the subtraction operation; the right-hand side
     * @return the difference (minuend - subtrahend)
     */
    SCORE subtract(@NonNull SCORE minuend, @NonNull SCORE subtrahend);

    /**
     * <p>
     * This method returns a value that encodes whether this Standings represents a better result for the passed Player
     * than the passed Standings (<code>that</code>) does.  The value is positive if this Standings is better, zero if
     * the two are equivalent, and negative if this Standings is worse.
     * </p>
     * <p>
     * This interface provides a default implementation that should lead to proper minimax comparisons for almost every
     * game.  It uses the following hierarchy of checks to determine which Standings is better.  If these checks do not
     * make sense for one's game, this method must be overridden.
     * </p>
     * <ol>
     *     <li>
     *         Is the passed Standings null?  this is always better than null.
     *     </li>
     *     <li>
     *         Does the passed Player have a better ranking in one Standings than the other?  Maximizing one's rating is
     *         the most likely path to lead to winning.
     *     </li>
     *     <li>
     *         Does the passed Player have sole leadership in one of the Standings and not the other?  Being the sole
     *         winner is better than tying.
     *     </li>
     *     <li>
     *         For each Standings, build a list of pairwise comparisons between the current Player's score and every
     *         other Player's score, sorted by the Players' rankings.  Does the Player score better than his opponents
     *         in one of the Standings?  For example, with differences (-3, -2, 0, 4) and (-3, -2, 0, 10), the current
     *         Player is in third place for both Standings, but scores 6 points better than fourth place in the second.
     *     </li>
     *     <li>
     *         Does the passed Player score better in one Standings than the other?
     *     </li>
     * </ol>
     * <p>
     * It should be worth noting that this method does not care how each individual participant performs, and therefore
     * does not represent a total ordering.  This is intentional.  If two Standings compare as equal for the passed
     * Player, they are mutually non-dominated.  Either is equally acceptable to the current Player.  The algorithm
     * should not be configured to punish any specific Player.  Each minimax agent should naturally assume that everyone
     * is trying to drive him into his worst-case outcome and therefore maximize that result.
     * </p>
     * @param that the Standings against which to compare this Standings's results
     * @param player the Player from whose perspective the two Standings must be compared
     * @return a positive value if this Standings is a better result for the passed Player, 0 if the two Standings are
     *   equivalent as far as the current Player is concerned, a negative value otherwise
     * @throws MismatchedPlaythroughException when the two Standings instances do not have the same set of participants
     * @throws InvalidPlayerException when the passed Player is not participating in the current game playthrough
     */
    default int compareAgainst(
        @CheckForNull STANDINGS that,
        @NonNull PLAYER player
    ) throws MismatchedPlaythroughException, InvalidPlayerException {
        int comparison;

        final var participants = getParticipants();

        if (!participants.contains(player)) {
            throw new InvalidPlayerException();
        } else if (that == null) {
            comparison = 1;
        } else if (!CollectionUtils.isEqualCollection(participants, that.getParticipants())) {
            throw new MismatchedPlaythroughException();
        } else {
            final var thatRanking = that.getRanking(player);
            final var thisRanking = this.getRanking(player);
            comparison = thatRanking - thisRanking;

            if (comparison == 0) {
                final var thatLeadership = that.isSoleLeader(player);
                final var thisLeadership = this.isSoleLeader(player);
                comparison = Boolean.compare(thisLeadership, thatLeadership);

                if (comparison == 0) {
                    comparison = compareScoreDifferences(that, participants, player);

                    if (comparison == 0) {
                        final var thatScore = that.getScore(player);
                        final var thisScore = this.getScore(player);
                        comparison = thisScore.compareTo(thatScore);
                    }
                }
            }
        }

        return comparison;
    }

    private int compareScoreDifferences(
        Standings<PLAYER, SCORE, STANDINGS> that,
        Set<PLAYER> participants,
        PLAYER player
    ) {
        int comparison = 0;

        final List<SCORE> thatDifferences = getDifferences(that, player, participants);
        final List<SCORE> thisDifferences = getDifferences(this, player, participants);
        for (int i = 0, count = participants.size(); comparison == 0 && i < count; ++i) {
            final var thatDifference = thatDifferences.get(i);
            final var thisDifference = thisDifferences.get(i);
            comparison = thisDifference.compareTo(thatDifference);
        }

        return comparison;
    }

    private List<SCORE> getDifferences(
        Standings<PLAYER, SCORE, STANDINGS> standings,
        PLAYER player,
        Set<PLAYER> participants
    ) {
        final var playerScore = standings.getScore(player);
        return participants.stream()
            .sorted(Comparator.comparing(standings::getRanking))
            .map(participant -> subtract(playerScore, standings.getScore(participant)))
            .toList();
    }

    /**
     * This helper method assists in comparing between two Standings when one or both of the variables might be null.
     * If neither is null, this returns the same result as calling <code>lhs.compareAgainst(rhs, player)</code>.
     * @param lhs the Standings on which to call <code>compareAgainst()</code>
     * @param rhs the Standings which will be passed to <code>lhs.compareAgainst()</code>
     * @param player the Player from whose perspective to compare the two Standings
     * @param <PLAYER> the Player's type
     * @param <SCORE> the Comparable type that captures each Player's score
     * @param <STANDINGS> the type that both Standings must have to ensure reasonable comparisons
     * @return <ul>
     *     <li>a positive number if lhs is not null and rhs is null</li>
     *     <li>zero if both lhs and rhs are null</li>
     *     <li>a negative number if lhs is null and rhs is not null</li>
     *     <li>whatever value lhs.compareAgainst(rhs, player) returns if neither are null</li>
     * </ul>
     * @throws MismatchedPlaythroughException when the two Standings instances do not have the same set of participants
     * @throws InvalidPlayerException when the passed Player is not participating in the current game playthrough
     */
    static <
        PLAYER extends Player,
        SCORE extends Comparable<SCORE>,
        STANDINGS extends Standings<PLAYER, SCORE, STANDINGS>
    > int compareNullable(
        @CheckForNull STANDINGS lhs,
        @CheckForNull STANDINGS rhs,
        @NonNull PLAYER player
    ) throws MismatchedPlaythroughException, InvalidPlayerException {
        int result;
        if (lhs == null) {
            result = rhs == null ? 0 : -1;
        } else if (rhs == null) {
            result = 1;
        } else {
            result = lhs.compareAgainst(rhs, player);
        }
        return result;
    }
}
