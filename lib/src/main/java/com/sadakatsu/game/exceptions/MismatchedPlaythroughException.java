package com.sadakatsu.game.exceptions;

import lombok.experimental.StandardException;

/**
 * It may be possible to accidentally attempt to compare two Standings objects that are not for the same playthrough.
 * The only way this library can detect such a scenario is if the two Standings do not have the same set of
 * participants.  Such a case is an IllegalArgumentException.
 */
@StandardException
public class MismatchedPlaythroughException extends IllegalArgumentException {}
