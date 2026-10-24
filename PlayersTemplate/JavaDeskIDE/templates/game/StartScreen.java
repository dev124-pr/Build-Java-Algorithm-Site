package templates.game;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StartScreen extends JFrame {
    private DefaultListModel<String> playerListModel;
    private JList<String> playerJList;
    private JButton startButton;
    private Timer syncTimer;

    private static final String SUPABASE_URL = "https://dayfulbpekslvtpwlwkb.supabase.co";
    private static final String SUPABASE_KEY = "sb_publishable_ZM5J48yz44WE3ztjrx1xdw_a6Yng8jM";
    private static final String STORAGE_BUCKET = "java-files";

    public StartScreen() {
        setTitle("Algorithm Battle - Start Screen");
        setSize(450, 550);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JLabel titleLabel = new JLabel("Algorithm Battle Arena", JLabel.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 22));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(20, 0, 10, 0));
        add(titleLabel, BorderLayout.NORTH);

        playerListModel = new DefaultListModel<>();
        playerJList = new JList<>(playerListModel);
        playerJList.setFont(new Font("Arial", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(playerJList);
        scrollPane.setBorder(BorderFactory.createTitledBorder(" Live Lobby (Auto-Syncing) "));
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));

        startButton = new JButton("START GAME");
        startButton.setFont(new Font("Arial", Font.BOLD, 18));
        startButton.setBackground(new Color(46, 204, 113));
        startButton.setForeground(Color.WHITE);
        startButton.setFocusPainted(false);
        startButton.setPreferredSize(new Dimension(180, 50));

        startButton.addActionListener(e -> startGame());
        bottomPanel.add(startButton);
        add(bottomPanel, BorderLayout.SOUTH);

        // סינכרון ראשוני מיד בהפעלה
        syncPlayers();

        // לופ שרץ כל שנייה ברקע ומעדכן את הלובי אוטומטית
        syncTimer = new Timer(1000, e -> checkServerUpdates());
        syncTimer.start();
    }

    private void syncPlayers() {
        playerListModel.clear();
        downloadAndSyncFiles();
    }

    private void checkServerUpdates() {
        new Thread(this::downloadAndSyncFiles).start();
    }

    private void downloadAndSyncFiles() {
        try {
            File playersDir = new File("D:\\BuildAnAlgorithm\\BuildAnAlgorithm\\Players");
            if (!playersDir.exists()) {
                playersDir.mkdirs();
            }

            HttpClient client = HttpClient.newHttpClient();
            String listUrl = SUPABASE_URL + "/storage/v1/object/list /" + STORAGE_BUCKET;
            // תיקון כתובת ה-URL שלא יהיו רווחים בטעות
            listUrl = SUPABASE_URL + "/storage/v1/object/list/" + STORAGE_BUCKET;

            String requestBody = "{\"prefix\": \"\", \"limit\": 100, \"offset\": 0}";

            HttpRequest listRequest = HttpRequest.newBuilder()
                    .uri(URI.create(listUrl))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> listResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
            if (listResponse.statusCode() != 200) return;

            String jsonList = listResponse.body();

            // חילוץ כל שמות הקבצים שמסתיימים ב-.java באמצעות Regex מדויק מתוך ה-JSON
            List<String> serverFileNames = new ArrayList<>();
            Matcher nameKeyMatcher = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+\\.java)\"").matcher(jsonList);
            while (nameKeyMatcher.find()) {
                serverFileNames.add(nameKeyMatcher.group(1));
            }

            List<String> activePlayerDisplayNames = new ArrayList<>();

            for (String fileName : serverFileNames) {
                String encodedPath = fileName.replace(" ", "%20");
                String fileUrl = SUPABASE_URL + "/storage/v1/object/" + STORAGE_BUCKET + "/" + encodedPath;

                HttpRequest fileRequest = HttpRequest.newBuilder()
                        .uri(URI.create(fileUrl))
                        .header("apikey", SUPABASE_KEY)
                        .header("Authorization", "Bearer " + SUPABASE_KEY)
                        .GET()
                        .build();

                HttpResponse<String> fileResponse = client.send(fileRequest, HttpResponse.BodyHandlers.ofString());
                if (fileResponse.statusCode() == 200) {
                    String fileContent = fileResponse.body();

                    // זיהוי שם המחלקה
                    String detectedClassName = "";
                    Matcher classMatcher = Pattern.compile("public\\s+class\\s+(\\w+)").matcher(fileContent);
                    if (classMatcher.find()) {
                        detectedClassName = classMatcher.group(1);
                    }
                    if (detectedClassName.isEmpty()) {
                        int lastSlash = fileName.lastIndexOf('/');
                        String shortName = lastSlash != -1 ? fileName.substring(lastSlash + 1) : fileName;
                        detectedClassName = shortName.substring(0, shortName.lastIndexOf('.'));
                    }

                    // חילוץ השם האמיתי של השחקן מתוך PLAYER_NAME_CHARS (תומך גם בפורמט מערך תווים וגם ב-String)
                    String displayName = detectedClassName;

                    // בדיקה האם מוגדר כמערך תווים כמו {'N', 'O', 'A', 'M'}
                    Matcher charArrayMatcher = Pattern.compile("PLAYER_NAME_CHARS\\s*=\\s*\\{([^}]+)\\}").matcher(fileContent);
                    if (charArrayMatcher.find()) {
                        String charsContent = charArrayMatcher.group(1);
                        StringBuilder sb = new StringBuilder();
                        Matcher charFinder = Pattern.compile("'([^']+)'").matcher(charsContent);
                        while (charFinder.find()) {
                            sb.append(charFinder.group(1));
                        }
                        if (sb.length() > 0) {
                            displayName = sb.toString().trim();
                        }
                    } else {
                        // בדיקה האם מוגדר כמחרוזת עם toCharArray()
                        Matcher stringNameMatcher = Pattern.compile("PLAYER_NAME_CHARS\\s*=\\s*\"([^\"]+)\"\\.toCharArray\\(\\)").matcher(fileContent);
                        if (stringNameMatcher.find()) {
                            displayName = stringNameMatcher.group(1).trim();
                        }
                    }

                    activePlayerDisplayNames.add(displayName);

                    // שמירת הקובץ בתיקייה המקומית
                    File targetFile = new File(playersDir, detectedClassName + ".java");
                    try (FileWriter writer = new FileWriter(targetFile)) {
                        writer.write(fileContent);
                    }

                    // עדכון ה-JList ב-UI Thread בצורה בטוחה
                    final String finalName = displayName;
                    SwingUtilities.invokeLater(() -> {
                        if (!playerListModel.contains(finalName)) {
                            playerListModel.addElement(finalName);
                        }
                    });
                }
            }

            // הסרת שחקנים מהרשימה במידה והקובץ נמחק מהשרת
            SwingUtilities.invokeLater(() -> {
                for (int i = playerListModel.size() - 1; i >= 0; i--) {
                    String listName = playerListModel.getElementAt(i);
                    if (!activePlayerDisplayNames.contains(listName)) {
                        playerListModel.remove(i);
                    }
                }
            });

        } catch (Exception ignored) {}
    }

    private void startGame() {
        if (syncTimer != null) syncTimer.stop();
        dispose();

        JFrame gameFrame = new JFrame("Algorithm Battle Game");
        gameFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        gameFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        gameFrame.setUndecorated(true);
        gameFrame.add(new GameBoard());
        gameFrame.setVisible(true);
    }
}