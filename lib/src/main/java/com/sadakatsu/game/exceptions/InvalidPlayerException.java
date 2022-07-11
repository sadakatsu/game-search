package com.sadakatsu.game.exceptions;

import lombok.experimental.StandardException;

/**
 * The InvalidPlayerException indicates that one of the library's querying methods (probably on Standings) was passed a
 * Player that is not participating in the current game.  This should only occur when multiple Players are defined for
 * the game, but not all Players necessarily play in every game.  One's code should query State::getPlayers() before
 * calling this method to ensure that one is not supplying an absent player.  This class is therefore an
 * IllegalArgumentException.
 */
@StandardException
public class InvalidPlayerException extends IllegalArgumentException {}
