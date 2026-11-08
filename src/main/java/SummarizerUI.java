import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;

public class SummarizerUI extends Application {

    private DatabaseManager dbManager;
    private List<String> existingTopics;

    // Pipeline UI Components
    private TextField urlInput;
    private TextField dirInput;
    private Label statusLabel;
    private ProgressIndicator spinner;
    private ListView<FileOrganizer.MoveTask> pendingList;
    private VBox reviewPanel;
    private Label currentFileLabel;
    private TextArea probArea, methArea, takeArea, notesArea;
    private ComboBox<String> topicBox;
    private TextField customTopicField;
    private Button moveBtn, skipBtn;
    private Button startButton, pauseButton, resumeButton, stopButton;

    // Cache Manager UI Components
    private ListView<CacheItem> cacheList;
    private Button scanCacheBtn, clearSelectedBtn, clearAllBtn;
    private Label cacheStatusLabel;

    // Browser UI Components
    private TextArea browseProbArea, browseMethArea, browseTakeArea, browseNotesArea;
    private TextField browseTopicField;
    private Button updateMetaBtn;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        dbManager = new DatabaseManager();
        existingTopics = dbManager.getAllTopics();
        primaryStage.setTitle("CS5013: Local Offline Research Paper Summarizer");

        BorderPane mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: #f4f7f9;"); 

        VBox sidebar = new VBox(20);
        sidebar.setPadding(new Insets(30, 20, 20, 20));
        sidebar.setStyle("-fx-background-color: #1a202c;"); 
        sidebar.setPrefWidth(240);

        Label logo = new Label("Research\nSummarizer");
        logo.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';");
        
        Button btnPipeline = createNavButton("1. Processing Pipeline");
        Button btnCache = createNavButton("2. Cache Manager");
        Button btnBrowser = createNavButton("3. Library Browser");

        sidebar.getChildren().addAll(logo, new Separator(), btnPipeline, btnCache, btnBrowser);

        StackPane contentArea = new StackPane();
        contentArea.setPadding(new Insets(20));

        VBox pipelineView = createPipelineTab(primaryStage);
        VBox cacheView = createCacheManagerTab();
        VBox browserView = createBrowserTab();

        contentArea.getChildren().setAll(pipelineView); 
        
        btnPipeline.setOnAction(e -> contentArea.getChildren().setAll(pipelineView));
        btnCache.setOnAction(e -> contentArea.getChildren().setAll(cacheView));
        btnBrowser.setOnAction(e -> {
            existingTopics = dbManager.getAllTopics();
            contentArea.getChildren().setAll(browserView);
        });

        mainLayout.setLeft(sidebar);
        mainLayout.setCenter(contentArea);

        Scene scene = new Scene(mainLayout, 1150, 850);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private Button createNavButton(String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #a0aec0; -fx-font-size: 15px; -fx-font-weight: bold; -fx-padding: 10 15 10 15; -fx-cursor: hand;");
        
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #2d3748; -fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold; -fx-padding: 10 15 10 15; -fx-background-radius: 8; -fx-cursor: hand;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #a0aec0; -fx-font-size: 15px; -fx-font-weight: bold; -fx-padding: 10 15 10 15; -fx-cursor: hand;"));
        return btn;
    }

    @Override
    public void stop() {
        System.exit(0);
    }

    private VBox createPipelineTab(Stage stage) {
        GridPane configPane = new GridPane();
        configPane.setHgap(10); configPane.setVgap(15); configPane.setPadding(new Insets(15));
        configPane.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 10, 0, 0, 4);");

        configPane.add(new Label("Local LLM URL:"), 0, 0);
        urlInput = new TextField("http://127.0.0.1:8080/v1");
        urlInput.setPrefWidth(350);
        configPane.add(urlInput, 1, 0);

        configPane.add(new Label("Target Folder:"), 0, 1);
        dirInput = new TextField();
        dirInput.setPromptText("Select folder containing PDFs...");
        dirInput.setPrefWidth(350);
        dirInput.setEditable(false);
        configPane.add(dirInput, 1, 1);

        Button browseButton = new Button("Browse...");
        browseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            File selected = chooser.showDialog(stage);
            if (selected != null) {
                dirInput.setText(selected.getAbsolutePath());
                statusLabel.setText("Status: Directory selected. Ready to process.");
            }
        });
        configPane.add(browseButton, 2, 1);

        startButton = new Button("Run Pipeline");
        startButton.setStyle("-fx-font-weight: bold; -fx-base: #3182ce; -fx-text-fill: white; -fx-background-radius: 6;");
        startButton.setOnAction(e -> handleStartProcessing());
        
        pauseButton = new Button("Pause");
        pauseButton.setStyle("-fx-font-weight: bold; -fx-base: #ecc94b; -fx-text-fill: #744210; -fx-background-radius: 6;");
        pauseButton.setDisable(true);
        pauseButton.setOnAction(e -> {
            LLMOrchestrator.pausePipeline();
            statusLabel.setText("Status: Pipeline paused.");
            pauseButton.setDisable(true);
            resumeButton.setDisable(false);
        });

        resumeButton = new Button("Resume");
        resumeButton.setStyle("-fx-font-weight: bold; -fx-base: #48bb78; -fx-text-fill: white; -fx-background-radius: 6;");
        resumeButton.setDisable(true);
        resumeButton.setOnAction(e -> {
            LLMOrchestrator.resumePipeline();
            statusLabel.setText("Status: Pipeline resuming...");
            resumeButton.setDisable(true);
            pauseButton.setDisable(false);
        });

        stopButton = new Button("Stop / Reset");
        stopButton.setStyle("-fx-font-weight: bold; -fx-base: #f56565; -fx-text-fill: white; -fx-background-radius: 6;");
        stopButton.setDisable(true);
        stopButton.setOnAction(e -> {
            LLMOrchestrator.cancelPipeline();
            statusLabel.setText("Status: Stopping pipeline...");
            pauseButton.setDisable(true);
            resumeButton.setDisable(true);
            stopButton.setDisable(true);
        });

        HBox controlPanel = new HBox(10, startButton, pauseButton, resumeButton, stopButton);
        configPane.add(controlPanel, 1, 2);

        SplitPane splitPane = new SplitPane();
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        pendingList = new ListView<>();
        pendingList.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(FileOrganizer.MoveTask item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.pdf.getName());
            }
        });
        
        pendingList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> loadReviewPanel(newVal));

        Label listHeader = new Label("Files Awaiting Review:");
        listHeader.setFont(Font.font("System", FontWeight.BOLD, 14));
        VBox listContainer = new VBox(10, listHeader, pendingList);
        listContainer.setPadding(new Insets(10));
        listContainer.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 10, 0, 0, 4);");
        VBox.setMargin(listContainer, new Insets(0, 10, 0, 0));
        VBox.setVgrow(pendingList, Priority.ALWAYS);

        reviewPanel = new VBox(15);
        reviewPanel.setPadding(new Insets(20));
        reviewPanel.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 10, 0, 0, 4);");
        reviewPanel.setDisable(true); 

        VBox scrollContent = new VBox(15);
        
        currentFileLabel = new Label("Select a file to review");
        currentFileLabel.setFont(Font.font("System", FontWeight.BOLD, 18));

        probArea = createReadOnlyTextArea();
        methArea = createReadOnlyTextArea();
        takeArea = createReadOnlyTextArea();
        notesArea = new TextArea();
        notesArea.setPromptText("Enter personal notes here (optional)...");
        notesArea.setWrapText(true);
        notesArea.setPrefRowCount(3);
        notesArea.setStyle("-fx-control-inner-background: #ffffff; -fx-border-color: #e2e8f0; -fx-border-radius: 5;");

        VBox topicSection = new VBox(10);
        topicSection.setPadding(new Insets(15));
        topicSection.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-radius: 5;");
        
        customTopicField = new TextField();
        customTopicField.setPromptText("Edit topics here (comma-separated)...");
        customTopicField.setStyle("-fx-control-inner-background: #ffffff; -fx-border-color: #e2e8f0; -fx-border-radius: 5;");

        topicBox = new ComboBox<>();
        topicBox.setMaxWidth(Double.MAX_VALUE);
        topicBox.setPromptText("Select to append an existing topic...");

        topicBox.setOnAction(e -> {
            String selected = topicBox.getValue();
            if (selected != null && !selected.trim().isEmpty()) {
                String current = customTopicField.getText().trim();
                if (current.isEmpty()) customTopicField.setText(selected);
                else if (!current.contains(selected)) customTopicField.setText(current + ", " + selected);
                Platform.runLater(() -> topicBox.getSelectionModel().clearSelection());
            }
        });

        topicSection.getChildren().addAll(
            createBoldLabel("1. Assigned Topics (Edit LLM suggestions here):"), customTopicField,
            createBoldLabel("2. Quick Add from Library:"), topicBox
        );

        scrollContent.getChildren().addAll(
            currentFileLabel,
            createBoldLabel("Identified Problem:"), probArea,
            createBoldLabel("Methodology:"), methArea,
            createBoldLabel("Key Takeaway:"), takeArea,
            createBoldLabel("My Notes:"), notesArea,
            topicSection
        );

        ScrollPane reviewScroll = new ScrollPane(scrollContent);
        reviewScroll.setFitToWidth(true);
        reviewScroll.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;");
        VBox.setVgrow(reviewScroll, Priority.ALWAYS); 

        moveBtn = new Button("Save & Cache File");
        moveBtn.setStyle("-fx-base: #48bb78; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 8 20 8 20; -fx-background-radius: 6;");
        skipBtn = new Button("Skip File");
        skipBtn.setStyle("-fx-base: #f56565; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 8 20 8 20; -fx-background-radius: 6;");
        
        HBox actionBox = new HBox(15, moveBtn, skipBtn);
        actionBox.setPadding(new Insets(10, 0, 0, 0)); 

        reviewPanel.getChildren().addAll(reviewScroll, actionBox);

        splitPane.getItems().addAll(listContainer, reviewPanel);
        splitPane.setDividerPositions(0.30);

        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(10, 15, 10, 15));
        statusBar.setStyle("-fx-background-color: #eeeeee; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusLabel = new Label("Status: Waiting for directory selection.");
        spinner = new ProgressIndicator(); spinner.setVisible(false); spinner.setPrefSize(18, 18);
        statusBar.getChildren().addAll(spinner, statusLabel);

        return new VBox(configPane, splitPane, statusBar);
    }

    private TextArea createReadOnlyTextArea() {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(3);
        area.setStyle("-fx-control-inner-background: #f8fafc; -fx-font-family: 'Segoe UI', sans-serif; -fx-border-color: #e2e8f0; -fx-border-radius: 5;");
        return area;
    }

    private Label createBoldLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 13));
        label.setTextFill(javafx.scene.paint.Color.web("#555555"));
        return label;
    }

    private void handleStartProcessing() {
        String url = urlInput.getText();
        String targetFolder = dirInput.getText();

        if (targetFolder == null || targetFolder.isEmpty()) {
            statusLabel.setText("Status: Error - Please select a target folder first.");
            return;
        }

        statusLabel.setText("Status: Verifying connection to local LLM...");
        
        startButton.setDisable(true);
        pauseButton.setDisable(false);
        stopButton.setDisable(false);
        resumeButton.setDisable(true);
        
        spinner.setVisible(true);
        pendingList.getItems().clear();

        new Thread(() -> {
            boolean isConnected = LLMOrchestrator.verifyLLMConnection(url);
            if (isConnected) {
                Platform.runLater(() -> statusLabel.setText("Status: Connected! Processing files... (Files will appear below as they finish)"));
                
                LLMOrchestrator.processLibrary(url, targetFolder, 
                    (task) -> {
                        if (task.summary != null) {
                            Platform.runLater(() -> pendingList.getItems().add(task));
                        }
                    },
                    () -> {
                        Platform.runLater(() -> {
                            spinner.setVisible(false);
                            startButton.setDisable(false);
                            pauseButton.setDisable(true);
                            resumeButton.setDisable(true);
                            stopButton.setDisable(true);
                            
                            if (pendingList.getItems().isEmpty()) {
                                statusLabel.setText("Status: Pipeline stopped or no new un-cached PDFs found.");
                            } else {
                                statusLabel.setText("Status: Pipeline complete. Select files from the list to review them.");
                                pendingList.getSelectionModel().selectFirst();
                            }
                        });
                    }
                );
            } else {
                Platform.runLater(() -> {
                    statusLabel.setText("Status: Connection failed. Check llama.cpp server and URL.");
                    spinner.setVisible(false); 
                    startButton.setDisable(false);
                    pauseButton.setDisable(true);
                    stopButton.setDisable(true);
                });
            }
        }).start();
    }

    private void loadReviewPanel(FileOrganizer.MoveTask task) {
        if (task == null) {
            reviewPanel.setDisable(true);
            currentFileLabel.setText("Select a file to review");
            probArea.setText(""); methArea.setText(""); takeArea.setText(""); notesArea.setText(""); customTopicField.setText("");
            return;
        }

        reviewPanel.setDisable(false);
        currentFileLabel.setText("Reviewing: " + task.pdf.getName());
        probArea.setText(task.summary.problem);
        methArea.setText(task.summary.methodology);
        takeArea.setText(task.summary.takeaway);
        notesArea.setText(""); 
        customTopicField.setText(task.summary.topic);

        topicBox.getItems().clear();
        for (String t : existingTopics) {
            topicBox.getItems().add(t);
        }

        moveBtn.setOnAction(null); skipBtn.setOnAction(null);

        moveBtn.setOnAction(e -> {
            String finalTopics = customTopicField.getText().trim();
            if (finalTopics.isEmpty()) finalTopics = "Uncategorized"; 
            
            task.summary.notes = notesArea.getText().trim();

            FileOrganizer.executeMove(task, finalTopics, dirInput.getText(), dbManager, existingTopics);
            pendingList.getItems().remove(task);
            statusLabel.setText("Status: Logically cached " + task.pdf.getName() + " with topics '" + finalTopics + "'.");
        });

        skipBtn.setOnAction(e -> {
            pendingList.getItems().remove(task);
            statusLabel.setText("Status: Skipped " + task.pdf.getName() + ".");
        });
    }

    private VBox createCacheManagerTab() {
        VBox layout = new VBox(15);
        layout.setStyle("-fx-background-color: transparent;"); 
        
        VBox cardContainer = new VBox(15);
        cardContainer.setPadding(new Insets(25));
        cardContainer.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 10, 0, 0, 4);");

        Label header = new Label("Database Cache Manager");
        header.setFont(Font.font("System", FontWeight.BOLD, 18));
        
        Label infoLabel = new Label("Clear records from the SQLite database to re-process files. Files will not be deleted from your hard drive.");
        infoLabel.setWrapText(true);

        HBox controls = new HBox(10);
        scanCacheBtn = new Button("Scan Directory for Cached Files");
        scanCacheBtn.setStyle("-fx-base: #3182ce; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 8 15 8 15;");
        clearSelectedBtn = new Button("Clear Selected Cache");
        clearSelectedBtn.setStyle("-fx-base: #e2e8f0; -fx-text-fill: #4a5568; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 8 15 8 15;");
        clearAllBtn = new Button("Clear ALL Listed Cache");
        clearAllBtn.setStyle("-fx-base: #f56565; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 8 15 8 15;");
        controls.getChildren().addAll(scanCacheBtn, clearSelectedBtn, clearAllBtn);

        cacheList = new ListView<>();
        cacheList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        cacheList.setStyle("-fx-control-inner-background: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 5;");
        VBox.setVgrow(cacheList, Priority.ALWAYS);

        cacheStatusLabel = new Label("Status: Ready.");
        cacheStatusLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #718096;");

        cardContainer.getChildren().addAll(header, infoLabel, controls, cacheList, cacheStatusLabel);
        VBox.setVgrow(cardContainer, Priority.ALWAYS);
        scanCacheBtn.setOnAction(e -> scanDirectoryForCache());
        clearSelectedBtn.setOnAction(e -> clearSelectedCache());
        clearAllBtn.setOnAction(e -> clearAllCache());
        layout.getChildren().add(cardContainer);
        return layout;
    }

    private void scanDirectoryForCache() {
        String targetFolder = dirInput.getText();
        if (targetFolder == null || targetFolder.isEmpty()) {
            cacheStatusLabel.setText("Status: Error - Please set the Target Folder in the Processing Pipeline tab first.");
            return;
        }

        cacheList.getItems().clear();
        java.util.List<File> supportedFiles = new java.util.ArrayList<>();
        
        try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(java.nio.file.Paths.get(targetFolder))) {
            supportedFiles = paths.filter(java.nio.file.Files::isRegularFile)
                    .map(java.nio.file.Path::toFile)
                    .filter(f -> f.getName().toLowerCase().matches(".*\\.(pdf|docx|md)$"))
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            cacheStatusLabel.setText("Status: Error scanning directory.");
            return;
        }

        if (supportedFiles.isEmpty()) {
            cacheStatusLabel.setText("Status: No supported files found.");
            return;
        }

        for (File file : supportedFiles) {
            String hash = DatabaseManager.computeFileHash(file);
            cacheList.getItems().add(new CacheItem(file, hash, dbManager.isCached(hash)));
        }
        cacheStatusLabel.setText("Status: Scan complete. Found " + supportedFiles.size() + " files.");
    }

    private void clearSelectedCache() {
        List<CacheItem> selected = cacheList.getSelectionModel().getSelectedItems();
        if (!selected.isEmpty()) executeCacheWipe(selected);
    }

    private void clearAllCache() {
        if (!cacheList.getItems().isEmpty()) executeCacheWipe(cacheList.getItems());
    }

    private void executeCacheWipe(List<CacheItem> itemsToClear) {
        String dbUrl = "jdbc:sqlite:papers.db";
        int count = 0;
        
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            PreparedStatement deleteCache = conn.prepareStatement("DELETE FROM processing_cache WHERE file_hash = ?");
            PreparedStatement deleteSummary = conn.prepareStatement("DELETE FROM summaries WHERE paper_id = (SELECT id FROM papers WHERE file_hash = ?)");
            PreparedStatement deletePaper = conn.prepareStatement("DELETE FROM papers WHERE file_hash = ?");

            for (CacheItem item : itemsToClear) {
                if (item.hash != null) {
                    deleteCache.setString(1, item.hash); deleteCache.executeUpdate();
                    deleteSummary.setString(1, item.hash); deleteSummary.executeUpdate();
                    deletePaper.setString(1, item.hash); deletePaper.executeUpdate();
                    count++;
                }
            }
            cacheStatusLabel.setText("Status: Successfully wiped cache records for " + count + " file(s).");
            scanDirectoryForCache(); 
        } catch (Exception e) {
            cacheStatusLabel.setText("Status: Error clearing cache - " + e.getMessage());
        }
    }

    private static class CacheItem {
        File file; String hash; boolean isCached;
        public CacheItem(File file, String hash, boolean isCached) {
            this.file = file; this.hash = hash; this.isCached = isCached;
        }
        @Override public String toString() { return (isCached ? "[CACHED] " : "[NOT CACHED] ") + file.getName(); }
    }

    private VBox createBrowserTab() {
        SplitPane splitPane = new SplitPane();
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        splitPane.setStyle("-fx-background-color: transparent; -fx-padding: 10;");

        VBox leftPane = new VBox(10);
        leftPane.setPadding(new Insets(15));
        leftPane.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 10, 0, 0, 4);");
        
        Button refreshBtn = new Button("Refresh Library Tree");
        refreshBtn.setStyle("-fx-base: #3182ce; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 8 15 8 15;");
        refreshBtn.setMaxWidth(Double.MAX_VALUE);
        
        TreeView<File> fileTree = new TreeView<>();
        fileTree.setStyle("-fx-control-inner-background: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 5;");
        VBox.setVgrow(fileTree, Priority.ALWAYS);
        
        fileTree.setCellFactory(tv -> new TreeCell<File>() {
            @Override
            protected void updateItem(File file, boolean empty) {
                super.updateItem(file, empty);
                setText((empty || file == null) ? null : file.getName());
            }
        });

        refreshBtn.setOnAction(e -> {
            String targetFolder = dirInput.getText();
            if (targetFolder != null && !targetFolder.isEmpty()) {
                File rootDir = new File(targetFolder);
                TreeItem<File> rootItem = new TreeItem<>(rootDir);
                rootItem.setExpanded(true);
                populateTree(rootDir, rootItem);
                fileTree.setRoot(rootItem);
            }
        });
        leftPane.getChildren().addAll(refreshBtn, fileTree);

        VBox rightPane = new VBox(15);
        rightPane.setPadding(new Insets(25));
        rightPane.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 10, 0, 0, 4);");
        VBox.setMargin(rightPane, new Insets(0, 0, 0, 10));
        
        browseTopicField = new TextField();
        browseTopicField.setStyle("-fx-control-inner-background: #ffffff; -fx-border-color: #e2e8f0; -fx-border-radius: 5;");
        browseTopicField.setPromptText("Comma-separated topics...");

        browseProbArea = createReadOnlyTextArea();
        browseMethArea = createReadOnlyTextArea();
        browseTakeArea = createReadOnlyTextArea();
        
        browseNotesArea = new TextArea();
        browseNotesArea.setWrapText(true);
        browseNotesArea.setPrefRowCount(4);
        browseNotesArea.setStyle("-fx-control-inner-background: #ffffff; -fx-border-color: #e2e8f0; -fx-border-radius: 5;");

        updateMetaBtn = new Button("Update Metadata");
        updateMetaBtn.setStyle("-fx-base: #3182ce; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 8 15 8 15;");
        updateMetaBtn.setDisable(true);

        Button exportBibtexBtn = new Button("Copy BibTeX Citation");
        exportBibtexBtn.setStyle("-fx-base: #48bb78; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 8 15 8 15;");
        exportBibtexBtn.setDisable(true); 
        
        Label statusMsg = new Label("");
        statusMsg.setStyle("-fx-text-fill: #38a169; -fx-font-style: italic; -fx-font-weight: bold;");
        HBox actionBox = new HBox(15, updateMetaBtn, exportBibtexBtn, statusMsg);
        actionBox.setAlignment(Pos.CENTER_LEFT);

        rightPane.getChildren().addAll(
            createBoldLabel("Assigned Topics (Comma-separated):"), browseTopicField,
            createBoldLabel("Identified Problem:"), browseProbArea,
            createBoldLabel("Methodology:"), browseMethArea,
            createBoldLabel("Key Takeaway:"), browseTakeArea,
            createBoldLabel("My Notes:"), browseNotesArea,
            actionBox
        );

        fileTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            statusMsg.setText(""); 
            if (newVal != null && newVal.getValue().isFile()) {
                File activeFile = newVal.getValue();
                PaperSummary summary = dbManager.getSummaryByPath(activeFile.getAbsolutePath());
                
                if (summary != null) {
                    browseTopicField.setText(summary.topic);
                    browseProbArea.setText(summary.problem);
                    browseMethArea.setText(summary.methodology);
                    browseTakeArea.setText(summary.takeaway);
                    browseNotesArea.setText(summary.notes);
                    
                    exportBibtexBtn.setDisable(false);
                    updateMetaBtn.setDisable(false);

                    updateMetaBtn.setOnAction(e -> {
                        dbManager.updatePaperMetadata(activeFile.getAbsolutePath(), browseTopicField.getText().trim(), browseNotesArea.getText().trim());
                        statusMsg.setText("Metadata Updated Successfully!");
                        statusMsg.setStyle("-fx-text-fill: #3182ce; -fx-font-style: italic; -fx-font-weight: bold;");
                    });
                    
                    exportBibtexBtn.setOnAction(e -> {
                        String fileName = activeFile.getName().replaceFirst("[.][^.]+$", ""); 
                        String citationKey = fileName.replaceAll("\\s+", "").replaceAll("[^a-zA-Z0-9]", "");
                        String bibtex = String.format(
                            "@misc{%s,\n  title = {%s},\n  keywords = {%s},\n  note = {Problem: %s \\n Methodology: %s}\n}",
                            citationKey, fileName, browseTopicField.getText(), summary.problem, summary.methodology
                        );
                        ClipboardContent content = new ClipboardContent();
                        content.putString(bibtex);
                        Clipboard.getSystemClipboard().setContent(content);
                        statusMsg.setText("Copied to clipboard!");
                        statusMsg.setStyle("-fx-text-fill: #38a169; -fx-font-style: italic; -fx-font-weight: bold;");
                    });
                } else {
                    browseTopicField.setText("Status: Not processed or cached yet.");
                    browseProbArea.clear(); browseMethArea.clear(); browseTakeArea.clear(); browseNotesArea.clear();
                    exportBibtexBtn.setDisable(true); updateMetaBtn.setDisable(true);
                }
            } else {
                exportBibtexBtn.setDisable(true); updateMetaBtn.setDisable(true);
            }
        });

        fileTree.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<File> selectedItem = fileTree.getSelectionModel().getSelectedItem();
                if (selectedItem != null && selectedItem.getValue().isFile()) openFileNative(selectedItem.getValue());
            }
        });

        splitPane.getItems().addAll(leftPane, rightPane);
        splitPane.setDividerPositions(0.30);
        return new VBox(splitPane);
    }

    private void populateTree(File dir, TreeItem<File> parent) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                TreeItem<File> treeItem = new TreeItem<>(file);
                parent.getChildren().add(treeItem);
                if (file.isDirectory()) populateTree(file, treeItem);
            }
        }
    }

    private void openFileNative(File file) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(file);
        } catch (Exception e) {
            System.err.println("Failed to open file: " + e.getMessage());
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("File Open Error");
                alert.setHeaderText("Could not open document");
                alert.setContentText("No default application is configured to open this file type.");
                alert.showAndWait();
            });
        }
    }
}