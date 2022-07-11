package com.sadakatsu.game.exceptions;

import lombok.experimental.StandardException;

/**
 * The GameOverException indicates that the code attempted to perform an Action against a State in which that Action is
 * illegal.  One's code should always check that the Action is legal in the current State before attempting to perform
 * it, so this exception should only be thrown when the programmer forgot to do so.  It is therefore an
 * IllegalArgumentException.  I considered making it an IllegalStateException to indicate how severe it is, but the
 * possibility of user input being involved led me to tone it down.
 */
@StandardException
public class IllegalActionException extends IllegalArgumentException {}
