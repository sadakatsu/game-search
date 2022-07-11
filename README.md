# game-search

This Java library is intended to bootstrap a project for a turn-based game.  It provides interfaces for implementing the
logic for a game in which a single player of many performs a single action at a time.  Implementing these interfaces
will enable the developer to combine their game with a static evaluation function to perform one of the included
searches.

This code base implements Tic-Tac-Toe in `lib/src/test` to both prove that the search algorithms function correctly and
to provide an example of how to implement the interfaces.