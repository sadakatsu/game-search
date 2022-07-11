package com.sadakatsu.game.domain;

/**
 * <p>
 * An Action is an atomic step a Player performs against a game State to generate a new State.  The classical abstract
 * strategy games would treat this interface as being synonymous with move, turn, or ply.  Other games might permit
 * multiple Actions one after the other during a single player's move.
 * </p>
 * <p>
 * This interface requires no methods.  There is a good chance that the implementation could be handled by an enum.
 * One implementing a game must ensure that the implementation must contain sufficient information to unambiguously
 * interpret the Action to be able to update the State correctly.
 * </p>
 */
public interface Action {}
