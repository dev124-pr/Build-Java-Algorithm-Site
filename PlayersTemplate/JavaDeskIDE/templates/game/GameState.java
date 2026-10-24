package templates.game;

import java.util.ArrayList;
import java.util.List;

public class GameState {
    private int width;
    private int height;
    private List<Target> targets;
    private List<PlayerInfo> players;

    // מחלקה פנימית שמייצגת מידע על שחקן על הלוח
    public static class PlayerInfo {
        public String name;
        public int x;
        public int y;
        public int score;
        public boolean isDisqualified;

        public PlayerInfo(String name, int x, int y, int score, boolean isDisqualified) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.score = score;
            this.isDisqualified = isDisqualified;
        }

        public String getName() {
            return name;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getScore() {
            return score;
        }

        public boolean isDisqualified() {
            return isDisqualified;
        }
    }

    // קונסטרוקטור ראשי המקבל את מימדי הלוח, רשימת המטרות ורשימת השחקנים
    public GameState(int width, int height, List<Target> targets, List<PlayerEntity> playerEntities) {
        this.width = width;
        this.height = height;
        this.targets = new ArrayList<>(targets);

        this.players = new ArrayList<>();
        if (playerEntities != null) {
            for (PlayerEntity p : playerEntities) {
                this.players.add(new PlayerInfo(p.name, p.x, p.y, p.score, p.isDisqualified));
            }
        }
    }

    // קונסטרוקטור משני לתאימות לאחור (במידה ולא מועברת רשימת שחקנים)
    public GameState(int width, int height, List<Target> targets) {
        this(width, height, targets, new ArrayList<>());
    }

    // מתודות שליפה (Getters)
    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public List<Target> getTargets() {
        return targets;
    }

    public List<PlayerInfo> getPlayers() {
        return players;
    }
}