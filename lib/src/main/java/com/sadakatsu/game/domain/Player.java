package com.sadakatsu.game.domain;

/**
 * <p>
 * The Player uniquely identifies one of the agents playing the game.  This is frequently tied to game-specific
 * information, such as whose pieces belong to that Player, where those pieces start, or even different abilities or
 * rules specific to that Player.  It might be best to implement Player using an enum, though more complicated games
 * might benefit from loading Player-specific information from configuration files and making that data accessible
 * through this class's instances.
 * </p>
 * <p>
 * This interface has three requirements:
 * <ol>
 *     <li>
 *         All Player instances to be used throughout a Game must be created before the initial State is created.  It is
 *         recommended that the logic instantiate them statically and reuse the same instances across different
 *         playthroughs.
 *     </li>
 *     <li>
 *         All Player instances must be immutable at least as far as the equals/hashCode contract is concerned.  Nearly
 *         every concrete class in this library uses Player as keys in maps.  It is strongly recommended that they be
 *         fully immutable.  It might be useful to modify Player data during a testing session, but that risks rendering
 *         the current game State as being invalid (as the Game state would benefit from calculating the legal Actions
 *         exactly once) and almost certainly risks invalidating any search that has been performed involving the
 *         current State.
 *     </li>
 * </ol>
 * </p>
 */
public interface Player {}
