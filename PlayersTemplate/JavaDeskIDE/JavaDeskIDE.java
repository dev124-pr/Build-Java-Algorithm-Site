import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.prefs.Preferences;

/** A small, local Java workspace. The application UI and logic are written in Java. */
public class JavaDeskIDE extends JFrame {
    private static final Color BACKGROUND = new Color(18, 20, 24);
    private static final Color PANEL = new Color(28, 31, 37);
    private static final Color PANEL_RAISED = new Color(36, 40, 47);
    private static final Color LINE = new Color(54, 60, 70);
    private static final Color TEXT = new Color(230, 233, 238);
    private static final Color MUTED = new Color(155, 162, 173);
    private static final Color ACCENT = new Color(104, 170, 255);
    private static final String BUILD_DIR_NAME = ".javadesk-build";

    private Path workspaceDir;
    private Path currentFile;
    private boolean currentFileOwned;
    private boolean dirty;

    private final DefaultMutableTreeNode treeRoot = new DefaultMutableTreeNode("Java Workspace");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(treeRoot);
    private final JTree fileTree = new JTree(treeModel);
    private final JTextArea editor = new JTextArea();
    private final JTextArea lineNumbers = new JTextArea("1");
    private final JTextArea console = new JTextArea();
    private final JLabel status = new JLabel("בחר פרויקט כדי להתחיל");
    private final JLabel editorTitle = new JLabel("אין קובץ פתוח");
    private JButton saveButton;
    private JButton runButton;

    public JavaDeskIDE() {
        super("JavaDesk — סביבת Java מקומית");
        workspaceDir = locateWorkspaceDirectory();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 640));
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
        configureTheme();
        buildUi();
        refreshTree();
    }

    private void configureTheme() {
        getContentPane().setBackground(BACKGROUND);
        editor.setBackground(new Color(25, 27, 30));
        editor.setForeground(TEXT);
        editor.setCaretColor(Color.WHITE);
        editor.setSelectionColor(new Color(52, 83, 125));
        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
        editor.setComponentOrientation(java.awt.ComponentOrientation.LEFT_TO_RIGHT);
        editor.setTabSize(4);
        editor.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        editor.setLineWrap(false);
        lineNumbers.setBackground(new Color(25, 27, 30));
        lineNumbers.setForeground(new Color(100, 108, 119));
        lineNumbers.setFont(editor.getFont());
        lineNumbers.setEditable(false);
        lineNumbers.setFocusable(false);
        lineNumbers.setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 8));
        console.setBackground(new Color(22, 24, 27));
        console.setForeground(new Color(196, 210, 193));
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        console.setComponentOrientation(java.awt.ComponentOrientation.LEFT_TO_RIGHT);
        console.setEditable(false);
        console.setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 10));
        status.setForeground(MUTED);
        editorTitle.setForeground(TEXT);
        editorTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        fileTree.setBackground(PANEL);
        fileTree.setForeground(TEXT);
        fileTree.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        fileTree.setCellRenderer(new WorkspaceTreeRenderer());
        fileTree.setRootVisible(false);
        fileTree.setShowsRootHandles(true);
        fileTree.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 4));
        fileTree.setRowHeight(25);
    }

    private void buildUi() {
        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setBackground(BACKGROUND);
        top.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE), BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JPanel leftActions = new JPanel();
        leftActions.setBackground(BACKGROUND);
        saveButton = button("שמירה");
        runButton = button("הרצת GameMain");
        runButton.setBackground(new Color(43, 105, 66));
        runButton.setForeground(Color.WHITE);
        leftActions.add(saveButton);
        top.add(leftActions, BorderLayout.WEST);
        top.add(runButton, BorderLayout.EAST);

        JPanel filePanel = new JPanel(new BorderLayout());
        filePanel.setBackground(PANEL);
        JLabel filePanelTitle = new JLabel("  PROJECT EXPLORER");
        filePanelTitle.setForeground(MUTED);
        filePanelTitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        filePanelTitle.setBorder(BorderFactory.createEmptyBorder(12, 4, 8, 4));
        filePanel.add(filePanelTitle, BorderLayout.NORTH);
        filePanel.add(new JScrollPane(fileTree), BorderLayout.CENTER);
        filePanel.setPreferredSize(new Dimension(290, 400));

        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.setBackground(BACKGROUND);
        editorTitle.setBorder(BorderFactory.createEmptyBorder(10, 13, 10, 10));
        editorPanel.add(editorTitle, BorderLayout.NORTH);
        JScrollPane editorScroll = new JScrollPane(editor);
        editorScroll.setRowHeaderView(lineNumbers);
        editorScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(55, 59, 66)));
        editorPanel.add(editorScroll, BorderLayout.CENTER);

        console.setRows(8);
        JScrollPane consoleScroll = new JScrollPane(console);
        consoleScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(55, 59, 66)), "פלט והרצה", 0, 0, new Font("Segoe UI", Font.PLAIN, 11), MUTED));
        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorPanel, consoleScroll);
        rightSplit.setResizeWeight(0.76);
        rightSplit.setBorder(BorderFactory.createEmptyBorder());
        rightSplit.setDividerSize(4);
        rightSplit.setBackground(BACKGROUND);
        styleSplitPane(rightSplit);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, filePanel, rightSplit);
        mainSplit.setResizeWeight(0.22);
        mainSplit.setDividerSize(5);
        mainSplit.setBorder(BorderFactory.createEmptyBorder());
        mainSplit.setDividerSize(4);
        mainSplit.setBackground(BACKGROUND);
        styleSplitPane(mainSplit);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(BACKGROUND);
        bottom.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
        bottom.add(status, BorderLayout.WEST);
        JLabel javaOnly = new JLabel("JAVA ONLY   ·   RUN: GameMain.java");
        javaOnly.setForeground(new Color(110, 121, 139));
        javaOnly.setFont(new Font("Consolas", Font.PLAIN, 10));
        bottom.add(javaOnly, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(mainSplit, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        saveButton.addActionListener(e -> saveCurrentFile());
        runButton.addActionListener(e -> runGameMain());
        fileTree.addTreeSelectionListener(e -> openSelectedFile());
        fileTree.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { maybeShowTreeMenu(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShowTreeMenu(e); }
        });
        editor.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { editorChanged(); }
            public void removeUpdate(DocumentEvent e) { editorChanged(); }
            public void changedUpdate(DocumentEvent e) { editorChanged(); }
        });
        editor.getInputMap().put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "save-file");
        editor.getActionMap().put("save-file", new javax.swing.AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { saveCurrentFile(); } });
        editor.getInputMap().put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "run-main");
        editor.getActionMap().put("run-main", new javax.swing.AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { runGameMain(); } });
        updateActions();
        SwingUtilities.invokeLater(() -> {
            mainSplit.setDividerLocation(0.24);
            rightSplit.setDividerLocation(0.74);
        });
    }

    private JButton button(String text) {
        JButton button = new JButton(text) {
            @Override protected void paintComponent(java.awt.Graphics graphics) {
                java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill = getBackground();
                if (!isEnabled()) fill = new Color(42, 45, 51);
                else if (getModel().isPressed()) fill = fill.darker();
                else if (getModel().isRollover()) fill = fill.brighter();
                g.setColor(fill);
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g.setColor(LINE);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        button.setFocusPainted(false);
        button.setForeground(TEXT);
        button.setBackground(PANEL_RAISED);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        button.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        return button;
    }

    private void styleSplitPane(JSplitPane pane) {
        pane.setUI(new BasicSplitPaneUI() {
            @Override public BasicSplitPaneDivider createDefaultDivider() {
                BasicSplitPaneDivider divider = new BasicSplitPaneDivider(this);
                divider.setBackground(BACKGROUND);
                divider.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, LINE));
                return divider;
            }
        });
    }

    private void maybeShowTreeMenu(MouseEvent event) {
        if (!event.isPopupTrigger()) return;
        TreePath path = fileTree.getPathForLocation(event.getX(), event.getY());
        if (path == null) return;
        fileTree.setSelectionPath(path);
        Object node = path.getLastPathComponent();
        if (!(node instanceof DefaultMutableTreeNode)) return;
        Object value = ((DefaultMutableTreeNode) node).getUserObject();
        if (!(value instanceof FileEntry)) return;
        FileEntry entry = (FileEntry) value;
        JPopupMenu menu = new JPopupMenu();
        if (!entry.directory && entry.path != null && entry.owned) {
            JMenuItem rename = new JMenuItem("Rename");
            rename.addActionListener(e -> renamePlayerFile(entry.path));
            menu.add(rename);
        } else return;
        menu.setBackground(Color.WHITE);
        menu.setBorder(BorderFactory.createLineBorder(new Color(190, 195, 203)));
        for (java.awt.Component component : menu.getComponents()) {
            if (component instanceof JMenuItem) {
                component.setBackground(Color.WHITE);
                component.setForeground(Color.BLACK);
                ((JMenuItem) component).setFont(new Font("Segoe UI", Font.PLAIN, 13));
            }
        }
        menu.show(fileTree, event.getX(), event.getY());
    }

    private Path playersDirectory() { return workspaceDir.resolve("templates").resolve("Players").toAbsolutePath().normalize(); }

    private void refreshTree() {
        treeRoot.removeAllChildren();
        DefaultMutableTreeNode workspaceRoot = new DefaultMutableTreeNode(new FileEntry(workspaceDir.getFileName().toString(), workspaceDir, false, true, false));
        addDirectoryChildren(workspaceRoot, workspaceDir);
        treeRoot.add(workspaceRoot);
        treeModel.reload();
        for (int i = 0; i < fileTree.getRowCount(); i++) fileTree.expandRow(i);
        updateActions();
    }

    private void addDirectoryChildren(DefaultMutableTreeNode parentNode, Path directory) {
        if (directory == null || !Files.isDirectory(directory)) return;
        try (java.util.stream.Stream<Path> entries = Files.list(directory)) {
            entries.filter(path -> !path.getFileName().toString().equals(BUILD_DIR_NAME) && !path.getFileName().toString().equals(".javadesk-owned-files"))
                    .sorted(Comparator.comparing((Path path) -> !Files.isDirectory(path)).thenComparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(path -> {
                        boolean dir = Files.isDirectory(path);
                        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new FileEntry(path.getFileName().toString(), path, isOwnedFile(path), dir, true));
                        parentNode.add(node);
                        if (dir) addDirectoryChildren(node, path);
                    });
        } catch (IOException ignored) { }
    }

    private void openSelectedFile() {
        Object selected = fileTree.getLastSelectedPathComponent();
        if (!(selected instanceof DefaultMutableTreeNode)) return;
        Object value = ((DefaultMutableTreeNode) selected).getUserObject();
        if (!(value instanceof FileEntry)) return;
        FileEntry entry = (FileEntry) value;
        if (entry.directory || entry.path == null) return;
        if (!entry.owned) { showInfo("זה קובץ מובנה. אפשר לראות שהוא קיים, אבל אי אפשר לפתוח או לערוך אותו."); return; }
        if (!confirmDiscardIfNeeded()) return;
        openFile(entry.path);
    }

    private void openFile(Path path) {
        try {
            currentFile = path.toAbsolutePath().normalize();
            currentFileOwned = isOwnedFile(currentFile);
            if (!currentFileOwned) { currentFile = null; showInfo("אפשר לפתוח ולערוך רק את קובץ השחקן בתיקיית templates\\Players."); return; }
            editor.setText(new String(Files.readAllBytes(currentFile), StandardCharsets.UTF_8));
            editor.setCaretPosition(0);
            dirty = false;
            editor.setEditable(true);
            editorTitle.setText(currentFile.getFileName().toString());
            updateLineNumbers();
            updateActions();
        } catch (IOException ex) { showError("לא ניתן לפתוח את הקובץ", ex); }
    }

    private void editorChanged() {
        if (currentFile == null) return;
        dirty = true;
        editorTitle.setText(currentFile.getFileName() + "  ·  שינויים לא נשמרו");
        updateLineNumbers();
        updateActions();
    }

    private void updateLineNumbers() {
        int count = Math.max(1, editor.getLineCount());
        StringBuilder numbers = new StringBuilder();
        for (int i = 1; i <= count; i++) numbers.append(i).append(i == count ? "" : "\n");
        lineNumbers.setText(numbers.toString());
    }

    private void saveCurrentFile() {
        if (currentFile == null || !currentFileOwned || !editor.isEditable()) return;
        try {
            if (!isOwnedFile(currentFile)) { showInfo("אפשר לשמור רק את קובץ השחקן שבתיקיית templates\\Players."); return; }
            Files.write(currentFile, editor.getText().getBytes(StandardCharsets.UTF_8));
            dirty = false;
            editorTitle.setText(currentFile.getFileName().toString());
            status.setText("נשמר: " + currentFile.getFileName());
            updateActions();
        } catch (IOException ex) { showError("לא ניתן לשמור את הקובץ", ex); }
    }

    private void renamePlayerFile(Path path) {
        if (!isOwnedFile(path)) return;
        if (!confirmDiscardIfNeeded()) return;
        String oldName = path.getFileName().toString();
        String entered = JOptionPane.showInputDialog(this, "שם חדש לקובץ (כולל ‎.java‎):", oldName);
        if (entered == null) return;
        String newName = entered.trim();
        if (!newName.matches("[A-Za-z_$][A-Za-z0-9_$]*\\.java")) {
            showInfo("יש לבחור שם Java תקין שמסתיים ב־.java, למשל Player.java.");
            return;
        }
        Path target = playersDirectory().resolve(newName).normalize();
        if (target.equals(path.toAbsolutePath().normalize())) return;
        if (Files.exists(target)) { showInfo("כבר קיים קובץ בשם הזה בתיקיית Players."); return; }
        try {
            String oldClassName = oldName.substring(0, oldName.length() - ".java".length());
            String newClassName = newName.substring(0, newName.length() - ".java".length());
            String source = Files.readString(path, StandardCharsets.UTF_8);
            Files.move(path, target);
            java.util.regex.Pattern publicType = java.util.regex.Pattern.compile(
                    "(?m)(\\bpublic\\s+(?:(?:abstract|final|sealed|non-sealed)\\s+)*(?:class|interface|enum|record)\\s+)"
                            + java.util.regex.Pattern.quote(oldClassName) + "\\b");
            String renamedSource = publicType.matcher(source).replaceAll("$1" + Matcher.quoteReplacement(newClassName));
            if (!renamedSource.equals(source)) Files.writeString(target, renamedSource, StandardCharsets.UTF_8);
            Preferences.userNodeForPackage(JavaDeskIDE.class).put("editable-player-file", workspaceDir.relativize(target).toString());
            if (currentFile != null && currentFile.equals(path.toAbsolutePath().normalize())) {
                currentFile = target;
                currentFileOwned = true;
                editorTitle.setText(newName);
            }
            refreshTree();
            status.setText("שם הקובץ שונה ל־" + newName);
        } catch (IOException ex) { showError("לא ניתן לשנות את שם הקובץ", ex); }
    }

    private boolean confirmDiscardIfNeeded() {
        if (!dirty) return true;
        int answer = JOptionPane.showConfirmDialog(this, "יש שינויים שלא נשמרו. לשמור לפני המעבר?", "שינויים לא נשמרו", JOptionPane.YES_NO_CANCEL_OPTION);
        if (answer == JOptionPane.CANCEL_OPTION || answer == JOptionPane.CLOSED_OPTION) return false;
        if (answer == JOptionPane.YES_OPTION) saveCurrentFile();
        return !dirty;
    }

    private void runGameMain() {
        if (dirty) saveCurrentFile();
        Path buildDir = workspaceDir.resolve(BUILD_DIR_NAME);
        runButton.setEnabled(false);
        console.setText("מתחיל קומפילציה…\n");
        new SwingWorker<String, String>() {
            @Override protected String doInBackground() throws Exception { return compileAndRun(buildDir); }
            @Override protected void done() {
                runButton.setEnabled(true);
                try { console.setText(get()); }
                catch (Exception ex) { console.setText("שגיאה בהרצה:\n" + rootMessage(ex)); }
                console.setCaretPosition(console.getDocument().getLength());
            }
        }.execute();
    }

    private String compileAndRun(Path buildDir) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("לא נמצא Java Compiler. יש לפתוח את האפליקציה עם JDK מותקן, לא עם JRE בלבד.");
        List<Path> sources = new ArrayList<>();
        Path templates = workspaceDir.resolve("templates");
        Path gameMain = templates.resolve("game").resolve("GameMain.java");
        if (!Files.isRegularFile(gameMain)) throw new IllegalStateException("לא נמצא קובץ ההרצה הקבוע: " + gameMain);
        collectJavaSources(templates, sources);
        if (!sources.contains(gameMain)) throw new IllegalStateException("GameMain.java לא נכלל בקומפילציה.");
        String mainClass = resolveMainClass(gameMain);
        deleteTree(buildDir);
        Files.createDirectories(buildDir);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StringBuilder output = new StringBuilder("קומפילציה…\n");
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = manager.getJavaFileObjectsFromPaths(sources);
            List<String> options = List.of("-encoding", "UTF-8", "-d", buildDir.toString());
            Boolean success = compiler.getTask(null, manager, diagnostics, options, null, units).call();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                output.append(diagnostic.getKind()).append(": ");
                if (diagnostic.getSource() != null) output.append(Paths.get(diagnostic.getSource().toUri()).getFileName()).append(":");
                if (diagnostic.getLineNumber() > 0) output.append(diagnostic.getLineNumber()).append(" — ");
                output.append(diagnostic.getMessage(Locale.getDefault())).append("\n");
            }
            if (!Boolean.TRUE.equals(success)) return output.append("\nהקומפילציה נכשלה. תקן את השגיאות ונסה שוב.\n").toString();
        }
        output.append("\nהקומפילציה הצליחה. מריץ ").append(mainClass).append("…\n\n");
        ProcessBuilder builder = new ProcessBuilder(javaExecutable(), "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-cp", buildDir.toString(), mainClass);
        builder.directory(workspaceDir.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        CompletableFuture<String> text = CompletableFuture.supplyAsync(() -> readProcessOutput(process));
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) { process.destroyForcibly(); return output.append(text.get(3, TimeUnit.SECONDS)).append("\nההרצה הופסקה אחרי 30 שניות.\n").toString(); }
        output.append(text.get(3, TimeUnit.SECONDS));
        output.append("\nתהליך הסתיים עם קוד ").append(process.exitValue()).append(".\n");
        return output.toString();
    }

    private String resolveMainClass(Path gameMain) throws IOException {
        String source = new String(Files.readAllBytes(gameMain), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*;").matcher(source);
        return matcher.find() ? matcher.group(1) + ".GameMain" : "GameMain";
    }

    private void collectJavaSources(Path root, List<Path> results) throws IOException {
        if (root == null || !Files.isDirectory(root)) return;
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .forEach(path -> results.add(path.toAbsolutePath().normalize()));
        }
    }

    private String javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
        Path bundled = Paths.get(System.getProperty("java.home"), "bin", executable);
        return Files.isExecutable(bundled) ? bundled.toString() : executable;
    }

    private String readProcessOutput(Process process) {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (result.length() < 1_000_000) result.append(line).append('\n');
            }
        } catch (IOException ex) { return "לא ניתן לקרוא את הפלט: " + ex.getMessage(); }
        return result.toString();
    }

    private void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException { if (exc != null) throw exc; Files.delete(dir); return FileVisitResult.CONTINUE; }
        });
    }

    private void updateActions() {
        saveButton.setEnabled(currentFile != null && currentFileOwned && editor.isEditable() && dirty);
        runButton.setEnabled(workspaceDir != null && Files.isDirectory(workspaceDir));
    }

    private void showInfo(String message) { JOptionPane.showMessageDialog(this, message, "JavaDesk", JOptionPane.INFORMATION_MESSAGE); }
    private void showError(String message, Exception exception) { JOptionPane.showMessageDialog(this, message + ":\n" + exception.getMessage(), "שגיאה", JOptionPane.ERROR_MESSAGE); }
    private String rootMessage(Throwable throwable) { Throwable root = throwable; while (root.getCause() != null) root = root.getCause(); return root.getMessage() == null ? root.toString() : root.getMessage(); }

    private static Path locateWorkspaceDirectory() {
        String saved = Preferences.userNodeForPackage(JavaDeskIDE.class).get("workspace-directory", "");
        if (!saved.isEmpty()) {
            Path savedPath = Paths.get(saved).toAbsolutePath().normalize();
            if (isJavaDeskRoot(savedPath)) return savedPath;
        }
        List<Path> starts = new ArrayList<>();
        starts.add(Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize());
        try { starts.add(Paths.get(JavaDeskIDE.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize()); }
        catch (Exception ignored) { }
        for (Path start : starts) {
            Path candidate = Files.isDirectory(start) ? start : start.getParent();
            while (candidate != null) {
                if (isJavaDeskRoot(candidate)) return candidate;
                // IntelliJ may launch from a parent project directory. Find the
                // JavaDeskIDE module below it, but use that module itself as root.
                try (java.util.stream.Stream<Path> found = Files.find(candidate, 4,
                        (path, attrs) -> attrs.isRegularFile() && path.getFileName().toString().equals("JavaDeskIDE.java"))) {
                    Path source = found.min(Comparator.comparingInt(Path::getNameCount)).orElse(null);
                    if (source != null) return source.getParent().toAbsolutePath().normalize();
                } catch (IOException ignored) { }
                candidate = candidate.getParent();
            }
        }
        return starts.get(0);
    }

    private static boolean isJavaDeskRoot(Path directory) {
        return directory != null && Files.isDirectory(directory)
                && (Files.isRegularFile(directory.resolve("JavaDeskIDE.java"))
                || Files.isRegularFile(directory.resolve("templates/game/GameMain.java")));
    }

    private Path editablePlayerFile() {
        String stored = Preferences.userNodeForPackage(JavaDeskIDE.class).get("editable-player-file", "");
        Path candidate = stored.isBlank() ? playersDirectory().resolve("player.java") : workspaceDir.resolve(stored).toAbsolutePath().normalize();
        if (candidate.getParent() != null && candidate.getParent().equals(playersDirectory())
                && candidate.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".java")) return candidate;
        return playersDirectory().resolve("player.java");
    }

    private boolean isOwnedFile(Path path) {
        return path != null && path.toAbsolutePath().normalize().equals(editablePlayerFile());
    }

    private static class FileEntry {
        final String name; final Path path; final boolean owned; final boolean directory; final boolean showLock;
        FileEntry(String name, Path path, boolean owned, boolean directory, boolean showLock) { this.name = name; this.path = path; this.owned = owned; this.directory = directory; this.showLock = showLock; }
        @Override public String toString() { return name; }
    }

    private static class WorkspaceTreeRenderer extends DefaultTreeCellRenderer {
        @Override public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            setForeground(selected ? Color.WHITE : TEXT);
            setBackgroundNonSelectionColor(PANEL);
            setBackgroundSelectionColor(new Color(47, 76, 112));
            Object user = ((DefaultMutableTreeNode) value).getUserObject();
            if (user instanceof FileEntry) {
                FileEntry entry = (FileEntry) user;
                setText(entry.name);
                javax.swing.Icon icon = UIManager.getIcon(entry.directory ? "FileView.directoryIcon" : "FileView.fileIcon");
                if (icon != null) setIcon(icon);
                if (!entry.owned && !entry.directory) setForeground(selected ? Color.WHITE : new Color(145, 153, 166));
                else if (entry.owned) setForeground(selected ? Color.WHITE : new Color(133, 190, 255));
            }
            return this;
        }
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
        UIManager.put("MenuItem.foreground", Color.BLACK);
        UIManager.put("MenuItem.background", Color.WHITE);
        UIManager.put("MenuItem.selectionForeground", Color.BLACK);
        UIManager.put("MenuItem.selectionBackground", new Color(225, 235, 250));
        SwingUtilities.invokeLater(() -> new JavaDeskIDE().setVisible(true));
    }
}
