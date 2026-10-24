package game;

import java.awt.Color;

public class Target {
    public int x, y;
    public int value;
    public Color color;

    public Target(int x, int y, int value, Color color) {
        this.x = x;
        this.y = y;
        this.value = value;
        this.color = color;
    }
}