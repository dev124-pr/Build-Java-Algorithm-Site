package game;

public interface PlayerAI {
    int getNextMove(int myX, int myY, GameState state);
}