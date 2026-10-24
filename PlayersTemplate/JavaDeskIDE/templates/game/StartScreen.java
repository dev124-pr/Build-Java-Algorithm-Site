package game;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StartScreen extends JFrame {
    private final DefaultListModel<String> playerListModel = new DefaultListModel<>();
    private final JButton startButton = new JButton("START GAME");
    private final Timer refreshTimer;

    public StartScreen() {
        setTitle("Algorithm Battle - Start Screen");
        setSize(450, 550);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JLabel title = new JLabel("Algorithm Battle Arena", JLabel.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 22));
        title.setBorder(BorderFactory.createEmptyBorder(20, 0, 10, 0));
        add(title, BorderLayout.NORTH);

        JList<String> list = new JList<>(playerListModel);
        list.setFont(new Font("Arial", Font.PLAIN, 14));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createTitledBorder(" Local Players folder "));
        add(scroll, BorderLayout.CENTER);

        startButton.setFont(new Font("Arial", Font.BOLD, 18));
        startButton.setBackground(new Color(46, 204, 113));
        startButton.setForeground(Color.WHITE);
        startButton.setFocusPainted(false);
        startButton.setPreferredSize(new Dimension(180, 50));
        startButton.addActionListener(e -> startGame());
        JPanel bottom = new JPanel();
        bottom.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));
        bottom.add(startButton);
        add(bottom, BorderLayout.SOUTH);

        refreshPlayers();
        refreshTimer = new Timer(1500, e -> refreshPlayers());
        refreshTimer.start();
    }

    private void refreshPlayers() {
        File dir = new File("templates/Players");
        if (!dir.exists()) dir.mkdirs();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".java") && !name.equals("temp_player.java"));
        List<String> names = new ArrayList<>();
        if (files != null) {
            for (File file : files) names.add(readDisplayName(file));
        }
        playerListModel.clear();
        for (String name : names) playerListModel.addElement(name);
        startButton.setEnabled(true);
    }

    private String readDisplayName(File file) {
        String source;
        try { source = java.nio.file.Files.readString(file.toPath()); }
        catch (Exception e) { return file.getName().replaceFirst("\\.java$", ""); }
        Matcher chars = Pattern.compile("PLAYER_NAME_CHARS\\s*=\\s*\\{([^}]+)\\}").matcher(source);
        if (chars.find()) {
            StringBuilder name = new StringBuilder();
            Matcher each = Pattern.compile("'([^']+)'" ).matcher(chars.group(1));
            while (each.find()) name.append(each.group(1));
            if (name.length() > 0) return name.toString().trim();
        }
        Matcher string = Pattern.compile("PLAYER_NAME_CHARS\\s*=\\s*\"([^\"]+)\"\\.toCharArray\\(\\)").matcher(source);
        if (string.find()) return string.group(1).trim();
        return file.getName().replaceFirst("\\.java$", "");
    }

    private void startGame() {
        File playersDir = new File("templates/Players").getAbsoluteFile();
        File[] files = playersDir.listFiles((d, name) -> name.endsWith(".java") && !name.equals("temp_player.java"));
        if (files == null || files.length == 0) {
            JOptionPane.showMessageDialog(this,
                    "לא נמצאו קובצי שחקנים (.java). שמור אותם בתיקייה:\n" + playersDir,
                    "תיקיית Players ריקה", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        refreshTimer.stop();
        dispose();
        JFrame frame = new JFrame("Algorithm Battle Game");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setUndecorated(true);
        frame.add(new GameBoard());
        frame.setVisible(true);
    }
}



