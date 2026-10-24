package game;

import java.util.ArrayList;
import java.util.List;

public class GameConsole {
    private final List<String> logs = new ArrayList<>();

    public void addLog(String message) {
        logs.add(message);
    }

    public List<String> getLogs() {
        return logs;
    }
}