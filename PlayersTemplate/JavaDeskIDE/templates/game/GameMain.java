package game;

import javax.swing.SwingUtilities;

public class GameMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            StartScreen screen = new StartScreen();
            screen.setVisible(true);
        });
    }
}