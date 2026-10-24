package game;

import java.awt.Color;

public class PlayerEntity {
    public String name;
    public int x;
    public int y;
    public Color color;
    public PlayerAI aiInstance;
    public int score;
    public boolean isDisqualified;
    public String deathReason;

    public PlayerEntity(String name, int x, int y, Color color, PlayerAI aiInstance) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.color = color;
        this.aiInstance = aiInstance;
        this.score = 0;
        this.isDisqualified = false;
        this.deathReason = "";
    }
}