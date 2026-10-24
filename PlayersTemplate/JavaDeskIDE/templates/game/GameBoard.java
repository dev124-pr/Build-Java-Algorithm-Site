package templates.game;

import javax.tools.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GameBoard extends JPanel implements ActionListener {
    private final int GRID_SIZE = 26;
    private final int SIDEBAR_WIDTH = 380;
    private Timer gameTimer;

    private List<PlayerEntity> players = new ArrayList<>();
    private List<Target> targets = new ArrayList<>();
    private int currentPlayerIndex = 0;

    private GameConsole gameConsole = new GameConsole();

    private int consoleScrollOffset = 0;
    private int leaderboardScrollOffset = 0;
    private boolean autoScrollConsole = true;

    private boolean isGameOver = false;
    private PlayerEntity winner = null;

    private static final String SUPABASE_URL = "https://dayfulbpekslvtpwlwkb.supabase.co";
    private static final String SUPABASE_KEY = "sb_publishable_ZM5J48yz44WE3ztjrx1xdw_a6Yng8jM";
    private static final String STORAGE_BUCKET = "java-files";

    public GameBoard() {
        setBackground(new Color(15, 23, 42));

        downloadJavaFilesRecursively("");
        loadPlayersDynamic();
        initTargets();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleMouseClick(e.getX(), e.getY());
            }
        });

        addMouseWheelListener(new MouseAdapter() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                handleMouseScroll(e.getX(), e.getY(), e.getWheelRotation());
            }
        });

        gameTimer = new Timer(500, this);
        gameTimer.start();

        gameConsole.addLog("Game started successfully!");
    }

    private void downloadJavaFilesRecursively(String prefix) {
        try {
            File playersDir = new File("D:\\BuildAnAlgorithm\\BuildAnAlgorithm\\Players");
            if (!playersDir.exists()) playersDir.mkdirs();

            HttpClient client = HttpClient.newHttpClient();
            String listUrl = SUPABASE_URL + "/storage/v1/object/list/" + STORAGE_BUCKET;
            String requestBody = "{\"prefix\": \"" + prefix + "\", \"limit\": 100, \"offset\": 0}";

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
            if (jsonList.startsWith("[") && jsonList.endsWith("]")) {
                String content = jsonList.substring(1, jsonList.length() - 1);
                if (content.trim().isEmpty()) return;

                String[] objects = content.split("\\},\\{");
                for (String obj : objects) {
                    String name = extractJsonValue(obj, "name");
                    if (!name.isEmpty()) {
                        String fullPath = prefix.isEmpty() ? name : prefix + name;
                        boolean isFolder = obj.contains("\"id\":null") || (!name.contains(".") && !obj.contains(".java"));

                        if (isFolder) {
                            downloadJavaFilesRecursively(fullPath + "/");
                        } else if (name.toLowerCase().endsWith(".java")) {
                            String encodedPath = fullPath.replace(" ", "%20");
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
                                String detectedClassName = "";
                                Matcher matcher = Pattern.compile("public\\s+class\\s+(\\w+)").matcher(fileContent);
                                if (matcher.find()) detectedClassName = matcher.group(1);

                                if (detectedClassName.isEmpty()) {
                                    detectedClassName = name.substring(0, name.lastIndexOf('.'));
                                }

                                File targetFile = new File(playersDir, detectedClassName + ".java");
                                try (FileWriter writer = new FileWriter(targetFile)) {
                                    writer.write(fileContent);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private String extractJsonValue(String jsonObj, String key) {
        String searchKey = "\"" + key + "\":";
        int idx = jsonObj.indexOf(searchKey);
        if (idx == -1) return "";
        int startIdx = idx + searchKey.length();
        if (jsonObj.charAt(startIdx) == '"') {
            startIdx++;
            int endIdx = jsonObj.indexOf('"', startIdx);
            if (endIdx != -1) return jsonObj.substring(startIdx, endIdx);
        }
        return "";
    }

    private void initTargets() {
        Random rand = new Random();
        int targetCount = players.size() + 20;
        targets.clear();
        for (int i = 0; i < targetCount; i++) {
            spawnTargetAvoidingEverything(rand);
        }
    }

    private void spawnTargetAvoidingEverything(Random rand) {
        int x, y;
        boolean collision;
        int safetyCounter = 0;
        do {
            x = rand.nextInt(GRID_SIZE);
            y = rand.nextInt(GRID_SIZE);
            collision = false;
            for (PlayerEntity p : players) {
                if (p.x == x && p.y == y) { collision = true; break; }
            }
            for (Target t : targets) {
                if (t.x == x && t.y == y) { collision = true; break; }
            }
            safetyCounter++;
            if (safetyCounter > 100) break;
        } while (collision);

        targets.add(new Target(x, y, 1, new Color(239, 68, 68)));
    }

    private void loadPlayersDynamic() {
        File playersDir = new File("D:\\BuildAnAlgorithm\\BuildAnAlgorithm\\Players");
        if (!playersDir.exists()) playersDir.mkdirs();

        File[] files = playersDir.listFiles((dir, name) -> name.endsWith(".java") && !name.equals("temp_player.java"));
        if (files == null || files.length == 0) return;

        Random rand = new Random();
        players.clear();

        for (File file : files) {
            String fileName = file.getName();
            String className = fileName.substring(0, fileName.lastIndexOf('.'));

            try {
                JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
                if (compiler != null) compiler.run(null, null, null, file.getPath());

                File srcParentDir = new File("D:\\BuildAnAlgorithm\\BuildAnAlgorithm");
                if (!srcParentDir.exists()) srcParentDir = new File(".");

                URLClassLoader classLoader = new URLClassLoader(new URL[]{srcParentDir.toURI().toURL()});
                Class<?> cls = Class.forName("Players." + className, true, classLoader);
                PlayerAI ai = (PlayerAI) cls.getDeclaredConstructor().newInstance();

                String displayName = className;
                try {
                    java.lang.reflect.Field field = cls.getField("PLAYER_NAME_CHARS");
                    char[] chars = (char[]) field.get(null);
                    displayName = new String(chars).trim();
                } catch (Exception ignored) {}

                Color playerColor;
                while (true) {
                    playerColor = new Color(50 + rand.nextInt(180), 50 + rand.nextInt(180), 50 + rand.nextInt(180));
                    if (!isForbiddenColor(playerColor)) break;
                }

                int startX, startY;
                boolean overlap;
                do {
                    startX = rand.nextInt(GRID_SIZE);
                    startY = rand.nextInt(GRID_SIZE);
                    overlap = false;
                    for (PlayerEntity p : players) {
                        if (p.x == startX && p.y == startY) overlap = true;
                    }
                    for (Target t : targets) {
                        if (t.x == startX && t.y == startY) overlap = true;
                    }
                } while (overlap);

                players.add(new PlayerEntity(displayName, startX, startY, playerColor, ai));

            } catch (Exception e) {
                PlayerEntity failedPlayer = new PlayerEntity(className, 0, 0, new Color(100, 100, 100), null);
                failedPlayer.isDisqualified = true;
                failedPlayer.deathReason = "compilation error";
                players.add(failedPlayer);
                gameConsole.addLog(className + " got KICKED due to compilation/code error!");
            }
        }
    }

    private boolean isForbiddenColor(Color c) {
        boolean isRed = c.getRed() > 200 && c.getGreen() < 50 && c.getBlue() < 50;
        boolean isBlack = c.getRed() < 40 && c.getGreen() < 40 && c.getBlue() < 40;
        return isRed || isBlack;
    }

    private void handleMouseClick(int mouseX, int mouseY) {
        int panelWidth = getWidth();
        int panelHeight = getHeight();

        if (isGameOver) {
            int centerX = panelWidth / 2;
            int centerY = panelHeight / 2;

            if (mouseX >= centerX - 210 && mouseX <= centerX - 10 && mouseY >= centerY + 60 && mouseY <= centerY + 115) {
                restartGameSamePlayers();
                return;
            }

            if (mouseX >= centerX + 10 && mouseX <= centerX + 210 && mouseY >= centerY + 60 && mouseY <= centerY + 115) {
                if (clearEverythingFromStorageAndLocal()) {
                    returnToStartScreen();
                }
                return;
            }
        }

        int rightPanelX = panelWidth - SIDEBAR_WIDTH;
        int btnX = rightPanelX + SIDEBAR_WIDTH - 55;
        int btnY = 25;
        int btnSize = 40;

        if (mouseX >= btnX && mouseX <= btnX + btnSize && mouseY >= btnY && mouseY <= btnY + btnSize) {
            if (clearEverythingFromStorageAndLocal()) {
                System.exit(0);
            }
        }
    }

    private void restartGameSamePlayers() {
        isGameOver = false;
        winner = null;
        gameConsole.getLogs().clear();
        gameConsole.addLog("Restarting match with same players...");

        Random rand = new Random();
        for (PlayerEntity p : players) {
            p.score = 0;
            p.isDisqualified = false;
            p.deathReason = "";
            p.x = rand.nextInt(GRID_SIZE);
            p.y = rand.nextInt(GRID_SIZE);
        }
        initTargets();
        if (gameTimer != null) gameTimer.start();
        repaint();
    }

    private void returnToStartScreen() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null) window.dispose();

        SwingUtilities.invokeLater(() -> {
            StartScreen startScreen = new StartScreen();
            startScreen.setVisible(true);
        });
    }

    private boolean clearEverythingFromStorageAndLocal() {
        try {
            HttpClient client = HttpClient.newHttpClient();

            // Check that this API key can actually see rows before claiming success.
            String tableUrl = SUPABASE_URL + "/rest/v1/java_files?id=not.is.null";
            HttpRequest tableCheckRequest = HttpRequest.newBuilder()
                    .uri(URI.create(tableUrl + "&select=id&limit=1"))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .GET()
                    .build();
            HttpResponse<String> tableBefore = client.send(tableCheckRequest, HttpResponse.BodyHandlers.ofString());
            requireSuccess("Database check", tableBefore.statusCode(), tableBefore.body());
            if (tableBefore.body().trim().equals("[]")) {
                throw new IllegalStateException("No rows are visible in public.java_files. The table may be wrong or RLS may hide its rows.");
            }

            HttpRequest tableDeleteRequest = HttpRequest.newBuilder()
                    .uri(URI.create(tableUrl))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Prefer", "return=representation")
                    .DELETE()
                    .build();
            HttpResponse<String> tableResponse = client.send(tableDeleteRequest, HttpResponse.BodyHandlers.ofString());
            requireSuccess("Database delete", tableResponse.statusCode(), tableResponse.body());
            if (tableResponse.body().trim().equals("[]")) {
                throw new IllegalStateException("Supabase accepted the request but deleted zero rows. Check the table name and DELETE RLS policy.");
            }
            HttpRequest tableVerifyRequest = HttpRequest.newBuilder()
                    .uri(URI.create(tableUrl + "&select=id&limit=1"))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .GET()
                    .build();
            HttpResponse<String> tableAfter = client.send(tableVerifyRequest, HttpResponse.BodyHandlers.ofString());
            requireSuccess("Database verification", tableAfter.statusCode(), tableAfter.body());
            if (!tableAfter.body().trim().equals("[]")) {
                throw new IllegalStateException("Rows are still visible in public.java_files after the delete request.");
            }

            // Deletes all files in the bucket through the Storage API.
            deleteStorageFolderRecursively(client, "");
            if (storageHasObjects(client, "")) {
                throw new IllegalStateException("Storage still contains files after the delete request. Check Storage SELECT/DELETE policies.");
            }

            File playersDir = new File("D:\\BuildAnAlgorithm\\BuildAnAlgorithm\\Players");
            if (playersDir.exists()) {
                File[] files = playersDir.listFiles();
                if (files == null) throw new IllegalStateException("Could not read local Players folder.");
                for (File f : files) {
                    if (f.isFile() && !f.delete()) {
                        throw new IllegalStateException("Could not delete local file: " + f.getAbsolutePath());
                    }
                }
            }
            gameConsole.addLog("Database, Storage, and local player files deleted.");
            return true;
        } catch (Exception e) {
            gameConsole.addLog("Delete failed: " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "המחיקה נכשלה:\n" + e.getMessage(), "שגיאה במחיקה", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void deleteStorageFolderRecursively(HttpClient client, String prefix) throws Exception {
        String listUrl = SUPABASE_URL + "/storage/v1/object/list/" + STORAGE_BUCKET;
        List<String> files = new ArrayList<>();
        List<String> folders = new ArrayList<>();
        int offset = 0;
        final int pageSize = 100;

        while (true) {
            String body = "{\"prefix\":\"" + jsonEscape(prefix) + "\",\"limit\":" + pageSize + ",\"offset\":" + offset + "}";
            HttpRequest listRequest = HttpRequest.newBuilder()
                    .uri(URI.create(listUrl))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
            requireSuccess("Storage list", response.statusCode(), response.body());

            List<String> entries = splitJsonArrayObjects(response.body());
            for (String entry : entries) {
                String name = extractJsonValue(entry, "name");
                if (name.isEmpty()) continue;
                String path = prefix.isEmpty() ? name : prefix + "/" + name;
                if (Pattern.compile("\\\"id\\\"\\s*:\\s*null").matcher(entry).find()) folders.add(path);
                else files.add(path);
            }
            if (entries.size() < pageSize) break;
            offset += pageSize;
        }

        for (String folder : folders) deleteStorageFolderRecursively(client, folder);

        // Supabase bulk removal accepts up to 1000 paths per request.
        for (int start = 0; start < files.size(); start += 1000) {
            List<String> batch = files.subList(start, Math.min(start + 1000, files.size()));
            StringBuilder body = new StringBuilder("{\"prefixes\":[");
            for (int i = 0; i < batch.size(); i++) {
                if (i > 0) body.append(',');
                body.append('"').append(jsonEscape(batch.get(i))).append('"');
            }
            body.append("]}");
            HttpRequest deleteRequest = HttpRequest.newBuilder()
                    .uri(URI.create(SUPABASE_URL + "/storage/v1/object/" + STORAGE_BUCKET))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
            requireSuccess("Storage delete", response.statusCode(), response.body());
        }
    }

    private List<String> splitJsonArrayObjects(String json) {
        List<String> objects = new ArrayList<>();
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        int start = -1;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
            } else if (c == '"') inString = true;
            else if (c == '{') {
                if (depth++ == 0) start = i;
            } else if (c == '}' && depth > 0 && --depth == 0) {
                objects.add(json.substring(start, i + 1));
            }
        }
        return objects;
    }

    private boolean storageHasObjects(HttpClient client, String prefix) throws Exception {
        String listUrl = SUPABASE_URL + "/storage/v1/object/list/" + STORAGE_BUCKET;
        int offset = 0;
        final int pageSize = 100;
        while (true) {
            String body = "{\"prefix\":\"" + jsonEscape(prefix) + "\",\"limit\":" + pageSize + ",\"offset\":" + offset + "}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(listUrl))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            requireSuccess("Storage verification", response.statusCode(), response.body());
            List<String> entries = splitJsonArrayObjects(response.body());
            for (String entry : entries) {
                String name = extractJsonValue(entry, "name");
                if (name.isEmpty()) continue;
                if (Pattern.compile("\\\"id\\\"\\s*:\\s*null").matcher(entry).find()) {
                    String childPrefix = prefix.isEmpty() ? name : prefix + "/" + name;
                    if (storageHasObjects(client, childPrefix)) return true;
                } else {
                    return true;
                }
            }
            if (entries.size() < pageSize) return false;
            offset += pageSize;
        }
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private void requireSuccess(String operation, int statusCode, String responseBody) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException(operation + " failed (HTTP " + statusCode + "): " + responseBody);
        }
    }

    private void handleMouseScroll(int mouseX, int mouseY, int wheelRotation) {
        if (isGameOver) return;
        int panelWidth = getWidth();
        int rightPanelX = panelWidth - SIDEBAR_WIDTH;

        if (mouseX < SIDEBAR_WIDTH) {
            int rowHeight = 65;
            int totalLeaderboardHeight = players.size() * rowHeight;
            int leaderboardAreaHeight = getHeight() - 120;
            int maxLbScroll = Math.max(0, totalLeaderboardHeight - leaderboardAreaHeight);

            if (maxLbScroll > 0) {
                leaderboardScrollOffset += wheelRotation * 25;
                if (leaderboardScrollOffset < 0) leaderboardScrollOffset = 0;
                if (leaderboardScrollOffset > maxLbScroll) leaderboardScrollOffset = maxLbScroll;
            } else {
                leaderboardScrollOffset = 0;
            }
        } else if (mouseX > rightPanelX) {
            consoleScrollOffset += wheelRotation * 25;
            if (consoleScrollOffset < 0) consoleScrollOffset = 0;
            autoScrollConsole = false;
        }
        repaint();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (isGameOver || players.isEmpty()) return;

        long activeCount = players.stream().filter(p -> !p.isDisqualified).count();
        if (activeCount <= 1) {
            isGameOver = true;
            if (activeCount == 1) {
                winner = players.stream().filter(p -> !p.isDisqualified).findFirst().orElse(null);
                if (winner != null) gameConsole.addLog(winner.name + " WON THE GAME!");
            } else {
                gameConsole.addLog("Game Over - No active players left!");
            }
            if (gameTimer != null) gameTimer.stop();
            repaint();
            return;
        }

        int attempts = 0;
        while (players.get(currentPlayerIndex).isDisqualified) {
            currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
            attempts++;
            if (attempts >= players.size()) return;
        }

        PlayerEntity p = players.get(currentPlayerIndex);
        GameState state = new GameState(GRID_SIZE, GRID_SIZE, targets, players);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Integer> future = executor.submit(() -> p.aiInstance.getNextMove(p.x, p.y, state));

        try {
            int moveDirection = future.get(1, TimeUnit.SECONDS);

            int nextX = p.x;
            int nextY = p.y;
            String moveDesc = "";

            switch (moveDirection) {
                case 0: if (p.y > 0) nextY--; moveDesc = "moved UP"; break;
                case 1: if (p.x < GRID_SIZE - 1) nextX++; moveDesc = "moved RIGHT"; break;
                case 2: if (p.y < GRID_SIZE - 1) nextY++; moveDesc = "moved DOWN"; break;
                case 3: if (p.x > 0) nextX--; moveDesc = "moved LEFT"; break;
            }

            p.x = nextX;
            p.y = nextY;

            gameConsole.addLog(p.name + " " + moveDesc);
            checkTargetCollision(p);
            checkPlayerCollision(p);

        } catch (Exception ex) {
            p.isDisqualified = true;
            p.deathReason = "timeout";
            gameConsole.addLog(p.name + " was disqualified (Timeout)!");
        } finally {
            executor.shutdownNow();
        }

        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        repaint();
    }

    private void checkTargetCollision(PlayerEntity p) {
        for (int i = 0; i < targets.size(); i++) {
            Target t = targets.get(i);
            if (t.x == p.x && t.y == p.y) {
                p.score += 1;
                gameConsole.addLog(p.name + " collected a target! Score: " + p.score);
                targets.remove(i);
                break;
            }
        }
    }

    private void checkPlayerCollision(PlayerEntity movingPlayer) {
        if (movingPlayer.isDisqualified) return;

        for (PlayerEntity other : players) {
            if (other == movingPlayer || other.isDisqualified) continue;

            if (other.x == movingPlayer.x && other.y == movingPlayer.y) {
                if (movingPlayer.score > other.score) {
                    other.isDisqualified = true;
                    other.deathReason = "killed by " + movingPlayer.name;
                    gameConsole.addLog(movingPlayer.name + " ate " + other.name + "!");
                } else if (other.score > movingPlayer.score) {
                    movingPlayer.isDisqualified = true;
                    movingPlayer.deathReason = "killed by " + other.name;
                    gameConsole.addLog(other.name + " ate " + movingPlayer.name + "!");
                    break;
                }
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int panelWidth = getWidth();
        int panelHeight = getHeight();

        int availableWidth = panelWidth - (SIDEBAR_WIDTH * 2);
        int boardSize = Math.min(availableWidth, panelHeight - 60);
        int TILE_SIZE = boardSize / GRID_SIZE;

        int boardWidth = GRID_SIZE * TILE_SIZE;
        int boardHeight = GRID_SIZE * TILE_SIZE;

        int startX = SIDEBAR_WIDTH + (availableWidth - boardWidth) / 2;
        int startY = (panelHeight - boardHeight) / 2;

        g2d.setColor(new Color(30, 41, 59));
        g2d.fillRoundRect(startX - 10, startY - 10, boardWidth + 20, boardHeight + 20, 20, 20);

        g2d.translate(startX, startY);

        g2d.setColor(new Color(51, 65, 85, 120));
        for (int i = 0; i <= GRID_SIZE; i++) {
            g2d.drawLine(i * TILE_SIZE, 0, i * TILE_SIZE, boardHeight);
            g2d.drawLine(0, i * TILE_SIZE, boardWidth, i * TILE_SIZE);
        }

        for (Target t : targets) {
            g2d.setColor(new Color(239, 68, 68));
            g2d.fillRoundRect(t.x * TILE_SIZE + 2, t.y * TILE_SIZE + 2, TILE_SIZE - 4, TILE_SIZE - 4, 6, 6);
        }

        for (PlayerEntity p : players) {
            if (p.isDisqualified) continue;

            int centerX = p.x * TILE_SIZE + TILE_SIZE / 2;
            int centerY = p.y * TILE_SIZE + TILE_SIZE / 2;
            int radius = Math.max(8, (TILE_SIZE - 8) / 2);

            g2d.setColor(new Color(0, 0, 0, 80));
            g2d.fillOval(centerX - radius, centerY - radius + 2, radius * 2, radius * 2);

            g2d.setColor(p.color);
            g2d.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

            g2d.setColor(Color.WHITE);
            g2d.setStroke(new BasicStroke(1.5f));
            g2d.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

            int fontSize = (int) Math.max(10, TILE_SIZE / 3.0);
            g2d.setFont(new Font("Segoe UI", Font.BOLD, fontSize));
            FontMetrics fmScore = g2d.getFontMetrics();
            String scoreStr = String.valueOf(p.score);
            int scoreX = centerX - fmScore.stringWidth(scoreStr) / 2;
            int scoreY = centerY + fmScore.getAscent() / 2 - 2;
            g2d.drawString(scoreStr, scoreX, scoreY);

            g2d.setColor(new Color(241, 245, 249));
            int nameFontSize = (int) Math.max(10, TILE_SIZE / 3.2);
            g2d.setFont(new Font("Segoe UI", Font.BOLD, nameFontSize));
            FontMetrics fmName = g2d.getFontMetrics();
            String nameStr = p.name;
            int nameX = centerX - fmName.stringWidth(nameStr) / 2;
            g2d.drawString(nameStr, nameX, p.y * TILE_SIZE - 6);
        }

        g2d.translate(-startX, -startY);

        // Sidebar Left (Leaderboard)
        g2d.setColor(new Color(30, 41, 59));
        g2d.fillRect(0, 0, SIDEBAR_WIDTH, panelHeight);
        g2d.setColor(new Color(51, 65, 85));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawLine(SIDEBAR_WIDTH, 0, SIDEBAR_WIDTH, panelHeight);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Segoe UI", Font.BOLD, 22));
        g2d.drawString("LEADERBOARD", 35, 60);

        List<PlayerEntity> sortedPlayers = new ArrayList<>(players);
        sortedPlayers.sort(Comparator.comparingInt((PlayerEntity p) -> p.score).reversed());

        int startYList = 100;
        int rowHeight = 65;
        int totalLeaderboardHeight = sortedPlayers.size() * rowHeight;
        int leaderboardAreaHeight = panelHeight - 120;
        int maxLbScroll = Math.max(0, totalLeaderboardHeight - leaderboardAreaHeight);

        if (maxLbScroll == 0) leaderboardScrollOffset = 0;
        else if (leaderboardScrollOffset > maxLbScroll) leaderboardScrollOffset = maxLbScroll;

        g2d.setClip(20, 85, SIDEBAR_WIDTH - 30, leaderboardAreaHeight);
        for (int i = 0; i < sortedPlayers.size(); i++) {
            PlayerEntity p = sortedPlayers.get(i);
            int yPos = startYList + (i * rowHeight) - leaderboardScrollOffset;

            g2d.setColor(new Color(15, 23, 42));
            g2d.fillRoundRect(25, yPos, SIDEBAR_WIDTH - 55, 54, 14, 14);

            if (i == 0 && !p.isDisqualified) {
                g2d.setColor(new Color(234, 179, 8));
                g2d.setStroke(new BasicStroke(1.5f));
                g2d.drawRoundRect(25, yPos, SIDEBAR_WIDTH - 55, 54, 14, 14);
            }

            g2d.setColor(p.isDisqualified ? new Color(100, 100, 100) : p.color);
            g2d.fillOval(45, yPos + 15, 24, 24);
            g2d.setColor(Color.WHITE);
            g2d.drawOval(45, yPos + 15, 24, 24);

            g2d.setColor(p.isDisqualified ? new Color(148, 163, 184, 150) : new Color(241, 245, 249));
            g2d.setFont(new Font("Segoe UI", Font.BOLD, 14));

            String displayName = p.name;
            if (p.isDisqualified) {
                if (p.deathReason != null && !p.deathReason.isEmpty()) {
                    displayName += " (" + p.deathReason + ")";
                } else {
                    displayName += " (died)";
                }
            }
            g2d.drawString(displayName, 82, yPos + 32);

            String pScore = String.valueOf(p.score);
            g2d.setColor(new Color(148, 163, 184));
            int scoreWidth = g2d.getFontMetrics().stringWidth(pScore);
            g2d.drawString(pScore, SIDEBAR_WIDTH - 70 - scoreWidth, yPos + 32);
        }
        g2d.setClip(null);

        // Sidebar Right (Match Stats & Console)
        int rightPanelX = panelWidth - SIDEBAR_WIDTH;
        g2d.setColor(new Color(30, 41, 59));
        g2d.fillRect(rightPanelX, 0, SIDEBAR_WIDTH, panelHeight);
        g2d.setColor(new Color(51, 65, 85));
        g2d.drawLine(rightPanelX, 0, rightPanelX, panelHeight);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Segoe UI", Font.BOLD, 22));
        g2d.drawString("MATCH STATS", rightPanelX + 35, 60);

        int btnX = rightPanelX + SIDEBAR_WIDTH - 55;
        int btnY = 25;
        int btnSize = 40;
        g2d.setColor(new Color(127, 29, 29, 180));
        g2d.fillRoundRect(btnX, btnY, btnSize, btnSize, 10, 10);
        g2d.setColor(new Color(248, 113, 113));
        g2d.setStroke(new BasicStroke(2.5f));
        g2d.drawLine(btnX + 11, btnY + 11, btnX + btnSize - 11, btnY + btnSize - 11);
        g2d.drawLine(btnX + 11, btnY + btnSize - 11, btnX + btnSize - 11, btnY + 11);

        g2d.setColor(new Color(15, 23, 42));
        g2d.fillRoundRect(rightPanelX + 25, 95, SIDEBAR_WIDTH - 55, 45, 14, 14);
        g2d.setColor(new Color(148, 163, 184));
        g2d.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g2d.drawString("Remaining Targets", rightPanelX + 38, 112);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g2d.drawString(String.valueOf(targets.size()), rightPanelX + 38, 131);

        g2d.setColor(new Color(15, 23, 42));
        g2d.fillRoundRect(rightPanelX + 25, 148, SIDEBAR_WIDTH - 55, 45, 14, 14);
        g2d.setColor(new Color(148, 163, 184));
        g2d.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g2d.drawString("Active Players", rightPanelX + 38, 165);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Segoe UI", Font.BOLD, 15));
        long activeCount = players.stream().filter(p -> !p.isDisqualified).count();
        g2d.drawString(String.valueOf(activeCount), rightPanelX + 38, 184);

        int consoleY = 205;
        int consoleHeight = panelHeight - consoleY - 15;
        g2d.setColor(new Color(15, 23, 42));
        g2d.fillRoundRect(rightPanelX + 25, consoleY, SIDEBAR_WIDTH - 55, consoleHeight, 14, 14);
        g2d.setColor(new Color(51, 65, 85));
        g2d.drawRoundRect(rightPanelX + 25, consoleY, SIDEBAR_WIDTH - 55, consoleHeight, 14, 14);

        g2d.setColor(new Color(56, 189, 248));
        g2d.setFont(new Font("Segoe UI", Font.BOLD, 14));
        g2d.drawString("CONSOLE LOG", rightPanelX + 40, consoleY + 25);

        g2d.setFont(new Font("Consolas", Font.PLAIN, 13));
        List<String> logs = gameConsole.getLogs();
        int logAreaTop = consoleY + 38;
        int logAreaHeight = consoleHeight - 45;
        g2d.setClip(rightPanelX + 30, logAreaTop, SIDEBAR_WIDTH - 65, logAreaHeight);

        int totalLogsHeight = logs.size() * 20;
        int maxScroll = Math.max(0, totalLogsHeight - logAreaHeight);
        if (autoScrollConsole) {
            consoleScrollOffset = maxScroll;
        } else {
            if (consoleScrollOffset > maxScroll) consoleScrollOffset = maxScroll;
            if (consoleScrollOffset >= maxScroll - 5) autoScrollConsole = true;
        }

        int logY = logAreaTop + 16 - consoleScrollOffset;
        for (String log : logs) {
            g2d.setColor(new Color(226, 232, 240));
            g2d.drawString(log, rightPanelX + 40, logY);
            logY += 20;
        }
        g2d.setClip(null);

        // מסך סיום משחק
        if (isGameOver) {
            g2d.setColor(new Color(15, 23, 42, 210));
            g2d.fillRect(0, 0, panelWidth, panelHeight);

            int boxWidth = 460;
            int boxHeight = 260;
            int boxX = (panelWidth - boxWidth) / 2;
            int boxY = (panelHeight - boxHeight) / 2;

            g2d.setColor(new Color(30, 41, 59));
            g2d.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 20, 20);
            g2d.setColor(new Color(234, 179, 8));
            g2d.setStroke(new BasicStroke(2f));
            g2d.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 20, 20);

            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Segoe UI", Font.BOLD, 26));
            String victoryText = winner != null ? "VICTORY!" : "GAME OVER";
            int vWidth = g2d.getFontMetrics().stringWidth(victoryText);
            g2d.drawString(victoryText, boxX + (boxWidth - vWidth) / 2, boxY + 50);

            g2d.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            g2d.setColor(new Color(148, 163, 184));
            String winnerSub = winner != null ? "Winner: " + winner.name : "No winner remaining";
            int wWidth = g2d.getFontMetrics().stringWidth(winnerSub);
            g2d.drawString(winnerSub, boxX + (boxWidth - wWidth) / 2, boxY + 85);

            int btnWidth = 200;
            int btnHeight = 50;
            int centerX = panelWidth / 2;
            int centerY = panelHeight / 2;

            g2d.setColor(new Color(46, 204, 113));
            g2d.fillRoundRect(centerX - 210, centerY + 60, btnWidth, btnHeight, 12, 12);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Segoe UI", Font.BOLD, 15));
            String btn1Text = "REMATCH";
            g2d.drawString(btn1Text, centerX - 210 + (btnWidth - g2d.getFontMetrics().stringWidth(btn1Text)) / 2, centerY + 92);

            g2d.setColor(new Color(239, 68, 68));
            g2d.fillRoundRect(centerX + 10, centerY + 60, btnWidth, btnHeight, 12, 12);
            g2d.setColor(Color.WHITE);
            String btn2Text = "NEW LOBBY";
            g2d.drawString(btn2Text, centerX + 10 + (btnWidth - g2d.getFontMetrics().stringWidth(btn2Text)) / 2, centerY + 92);
        }
    }
}
