package com.sadakatsu.game.exceptions;

import lombok.experimental.StandardException;

/**
 * The GameOverException indicates that the code attempted to perform an Action against a terminal State.  One's code
 * should always check both that the State is not terminal and that there are any legal Actions before attempting to
 * perform the Action, so this exception should only be thrown when the programmer forgot to perform these checks.  It
 * is therefore an IllegalStateException.
 */
@StandardException
public class GameOverException extends IllegalStateException {}
