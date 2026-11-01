import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
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

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import java.awt.image.BufferedImage;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;

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
    private TextArea probArea, methArea, takeArea;
    private ComboBox<String> topicBox;
    private TextField customTopicField;
    private Button moveBtn, skipBtn, startButton;

    // Cache Manager UI Components
    private ListView<CacheItem> cacheList;
    private Button scanCacheBtn, clearSelectedBtn, clearAllBtn;
    private Label cacheStatusLabel;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        dbManager = new DatabaseManager();
        existingTopics = dbManager.getAllTopics();

        primaryStage.setTitle("CS5013: Local Offline Research Paper Summarizer");

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab pipelineTab = new Tab("1. Processing Pipeline", createPipelineTab(primaryStage));
        Tab cacheTab = new Tab("2. Cache Manager", createCacheManagerTab());
        Tab browserTab = new Tab("3. Library Browser", createBrowserTab());
        
        // ADD TABS ONLY ONCE:
        tabPane.getTabs().addAll(pipelineTab, cacheTab, browserTab); 
        
        // I REMOVED THE DUPLICATE LINE THAT WAS RIGHT HERE.

        Scene scene = new Scene(tabPane, 1000, 750);
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    @Override
        public void stop() {
            System.out.println("Closing application and terminating background LLM threads...");
            System.exit(0);
        }
    // =========================================================================
    // TAB 1: FULL PROCESSING PIPELINE & FILE-BY-FILE REVIEW
    // =========================================================================
    private VBox createPipelineTab(Stage stage) {
        // --- TOP CONFIGURATION ---
        GridPane configPane = new GridPane();
        configPane.setHgap(10);
        configPane.setVgap(15);
        configPane.setPadding(new Insets(15));
        configPane.setStyle("-fx-background-color: #f4f4f4; -fx-border-color: #dcdcdc; -fx-border-width: 0 0 1 0;");

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
            chooser.setTitle("Select PDF Directory");
            File selected = chooser.showDialog(stage);
            if (selected != null) {
                dirInput.setText(selected.getAbsolutePath());
                statusLabel.setText("Status: Directory selected. Ready to process.");
            }
        });
        configPane.add(browseButton, 2, 1);

        startButton = new Button("Verify Connection & Run Pipeline");
        startButton.setStyle("-fx-font-weight: bold; -fx-base: #2196F3; -fx-text-fill: white; -fx-font-size: 14px;");
        startButton.setMaxWidth(Double.MAX_VALUE);
        startButton.setOnAction(e -> handleStartProcessing());
        configPane.add(startButton, 1, 2);

        // --- SPLIT VIEW: PENDING LIST (LEFT) & REVIEW PANEL (RIGHT) ---
        SplitPane splitPane = new SplitPane();
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        // Left Side: List of files waiting for review
        pendingList = new ListView<>();
        pendingList.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(FileOrganizer.MoveTask item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(item.pdf.getName());
            }
        });
        
        pendingList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            loadReviewPanel(newVal);
        });

        Label listHeader = new Label("Files Awaiting Review:");
        listHeader.setFont(Font.font("System", FontWeight.BOLD, 14));
        VBox listContainer = new VBox(10, listHeader, pendingList);
        listContainer.setPadding(new Insets(10));
        VBox.setVgrow(pendingList, Priority.ALWAYS);

        // Right Side: Detailed Review Panel
        reviewPanel = new VBox(15);
        reviewPanel.setPadding(new Insets(20));
        reviewPanel.setStyle("-fx-background-color: #ffffff;");
        reviewPanel.setDisable(true); // Disabled until a file is selected

        currentFileLabel = new Label("Select a file to review");
        currentFileLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        currentFileLabel.setTextFill(javafx.scene.paint.Color.web("#333333"));

        probArea = createReadOnlyTextArea();
        methArea = createReadOnlyTextArea();
        takeArea = createReadOnlyTextArea();

        // Topic Selection Card
        VBox topicSection = new VBox(10);
        topicSection.setPadding(new Insets(15));
        topicSection.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-radius: 5; -fx-background-radius: 5;");
        
        Label topicLabel1 = new Label("1. Select an Existing Folder:");
        topicLabel1.setFont(Font.font("System", FontWeight.BOLD, 12));
        topicBox = new ComboBox<>();
        topicBox.setMaxWidth(Double.MAX_VALUE);

        Label topicLabel2 = new Label("2. OR Type a New Folder Name:");
        topicLabel2.setFont(Font.font("System", FontWeight.BOLD, 12));
        customTopicField = new TextField();
        customTopicField.setPromptText("Type custom name here (this will override the dropdown)...");

        topicSection.getChildren().addAll(topicLabel1, topicBox, topicLabel2, customTopicField);

        // Action Buttons
        moveBtn = new Button("Move & Cache File");
        moveBtn.setStyle("-fx-base: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 8 20 8 20;");
        skipBtn = new Button("Skip File");
        skipBtn.setStyle("-fx-base: #f44336; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 8 20 8 20;");
        HBox actionBox = new HBox(15, moveBtn, skipBtn);
        actionBox.setAlignment(Pos.CENTER_LEFT);

        reviewPanel.getChildren().addAll(
            currentFileLabel,
            createBoldLabel("Identified Problem:"), probArea,
            createBoldLabel("Methodology:"), methArea,
            createBoldLabel("Key Takeaway:"), takeArea,
            topicSection,
            actionBox
        );

        ScrollPane reviewScroll = new ScrollPane(reviewPanel);
        reviewScroll.setFitToWidth(true);
        reviewScroll.setStyle("-fx-background-color: transparent;");

        splitPane.getItems().addAll(listContainer, reviewScroll);
        splitPane.setDividerPositions(0.30);

        // --- BOTTOM STATUS BAR ---
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(10, 15, 10, 15));
        statusBar.setStyle("-fx-background-color: #eeeeee; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusLabel = new Label("Status: Waiting for directory selection.");
        spinner = new ProgressIndicator();
        spinner.setVisible(false);
        spinner.setPrefSize(18, 18);
        statusBar.getChildren().addAll(spinner, statusLabel);

        return new VBox(configPane, splitPane, statusBar);
    }

    private TextArea createReadOnlyTextArea() {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(3);
        area.setStyle("-fx-control-inner-background: #fdfdfd; -fx-font-family: 'Segoe UI', sans-serif;");
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
        spinner.setVisible(true);
        pendingList.getItems().clear();

        new Thread(() -> {
            boolean isConnected = LLMOrchestrator.verifyLLMConnection(url);

            if (isConnected) {
                Platform.runLater(() -> statusLabel.setText("Status: Connected! Extracting and summarizing PDFs..."));
                List<FileOrganizer.MoveTask> tasks = LLMOrchestrator.processLibrary(url, targetFolder);
                
                Platform.runLater(() -> {
                    spinner.setVisible(false);
                    startButton.setDisable(false);
                    for (FileOrganizer.MoveTask task : tasks) {
                        // REMOVED the strict filter so all processed files appear in the list
                        if (task.summary != null) {
                            pendingList.getItems().add(task);
                        }
                    }
                    if (pendingList.getItems().isEmpty()) {
                        statusLabel.setText("Status: No new un-cached PDFs found.");
                    } else {
                        statusLabel.setText("Status: Pipeline complete. Select files from the list to organize them.");
                        pendingList.getSelectionModel().selectFirst();
                    }
                });
            } else {
                Platform.runLater(() -> {
                    statusLabel.setText("Status: Connection failed. Check llama.cpp server and URL.");
                    spinner.setVisible(false);
                    startButton.setDisable(false);
                });
            }
        }).start();
    }

    private void loadReviewPanel(FileOrganizer.MoveTask task) {
        if (task == null) {
            reviewPanel.setDisable(true);
            currentFileLabel.setText("Select a file to review");
            probArea.setText(""); methArea.setText(""); takeArea.setText("");
            customTopicField.setText("");
            return;
        }

        reviewPanel.setDisable(false);
        currentFileLabel.setText("Reviewing: " + task.pdf.getName());
        probArea.setText(task.summary.problem);
        methArea.setText(task.summary.methodology);
        takeArea.setText(task.summary.takeaway);

        topicBox.getItems().clear();
        topicBox.getItems().add(task.summary.topic + " (LLM Suggestion)");
        for (String t : existingTopics) {
            if (!t.equalsIgnoreCase(task.summary.topic)) {
                topicBox.getItems().add(t);
            }
        }
        topicBox.getSelectionModel().selectFirst();
        customTopicField.setText(""); // Clear custom input for new file

        // Ensure actions aren't duplicated if selected multiple times
        moveBtn.setOnAction(null);
        skipBtn.setOnAction(null);

        moveBtn.setOnAction(e -> {
            String customName = customTopicField.getText().trim();
            String selectedTopic;
            
            // Explicit logic to prioritize the custom text field over the dropdown
            if (!customName.isEmpty()) {
                selectedTopic = customName;
            } else {
                selectedTopic = topicBox.getValue().replace(" (LLM Suggestion)", "").trim();
            }

            FileOrganizer.executeMove(task, selectedTopic, dirInput.getText(), dbManager, existingTopics);
            pendingList.getItems().remove(task);
            statusLabel.setText("Status: Moved " + task.pdf.getName() + " to '" + selectedTopic + "'.");
        });

        skipBtn.setOnAction(e -> {
            pendingList.getItems().remove(task);
            statusLabel.setText("Status: Skipped " + task.pdf.getName() + ".");
        });
    }

    // =========================================================================
    // TAB 2: CACHE MANAGER
    // =========================================================================
    private VBox createCacheManagerTab() {
        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: #fefefe;");

        Label header = new Label("Database Cache Manager");
        header.setFont(Font.font("System", FontWeight.BOLD, 18));
        
        Label infoLabel = new Label("Scan your target folder and delete specific records from the SQLite database. This allows you to re-process and test files without wiping your entire library history.");
        infoLabel.setWrapText(true);

        HBox controls = new HBox(10);
        scanCacheBtn = new Button("Scan Directory for Cached Files");
        scanCacheBtn.setStyle("-fx-base: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        clearSelectedBtn = new Button("Clear Selected Cache");
        clearAllBtn = new Button("Clear ALL Listed Cache");
        clearAllBtn.setStyle("-fx-base: #f44336; -fx-text-fill: white;");
        
        controls.getChildren().addAll(scanCacheBtn, clearSelectedBtn, clearAllBtn);

        cacheList = new ListView<>();
        cacheList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE); 
        VBox.setVgrow(cacheList, Priority.ALWAYS);

        cacheStatusLabel = new Label("Status: Ready.");
        cacheStatusLabel.setStyle("-fx-font-style: italic;");

        layout.getChildren().addAll(header, infoLabel, controls, cacheList, cacheStatusLabel);

        scanCacheBtn.setOnAction(e -> scanDirectoryForCache());
        clearSelectedBtn.setOnAction(e -> clearSelectedCache());
        clearAllBtn.setOnAction(e -> clearAllCache());

        return layout;
    }

    private void scanDirectoryForCache() {
        String targetFolder = dirInput.getText();
        if (targetFolder == null || targetFolder.isEmpty()) {
            cacheStatusLabel.setText("Status: Error - Please set the Target Folder in the Processing Pipeline tab first.");
            return;
        }

        cacheList.getItems().clear();
        List<File> pdfs = DocumentExtractor.getSupportedFilesInDirectory(targetFolder);
        
        if (pdfs.isEmpty()) {
            cacheStatusLabel.setText("Status: No PDFs found in the selected folder.");
            return;
        }

        for (File pdf : pdfs) {
            String hash = DatabaseManager.computeFileHash(pdf);
            boolean isCached = dbManager.isCached(hash);
            cacheList.getItems().add(new CacheItem(pdf, hash, isCached));
        }
        cacheStatusLabel.setText("Status: Scan complete. Found " + pdfs.size() + " files.");
    }

    private void clearSelectedCache() {
        List<CacheItem> selected = cacheList.getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) return;
        executeCacheWipe(selected);
    }

    private void clearAllCache() {
        List<CacheItem> allItems = cacheList.getItems();
        if (allItems.isEmpty()) return;
        executeCacheWipe(allItems);
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
        File file;
        String hash;
        boolean isCached;

        public CacheItem(File file, String hash, boolean isCached) {
            this.file = file;
            this.hash = hash;
            this.isCached = isCached;
        }

        @Override
        public String toString() {
            return (isCached ? "[CACHED] " : "[NOT CACHED] ") + file.getName();
        }
    }
    private TextArea browseProbArea, browseMethArea, browseTakeArea;
    private Label browseTopicLabel;

    private VBox createBrowserTab() {
        SplitPane splitPane = new SplitPane();
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        // Left Side: File Tree
        VBox leftPane = new VBox(10);
        leftPane.setPadding(new Insets(10));
        Button refreshBtn = new Button("Refresh Library Tree");
        TreeView<File> fileTree = new TreeView<>();
        VBox.setVgrow(fileTree, Priority.ALWAYS);
        
        fileTree.setCellFactory(tv -> new TreeCell<File>() {
            @Override
            protected void updateItem(File file, boolean empty) {
                super.updateItem(file, empty);
                if (empty || file == null) {
                    setText(null);
                } else {
                    setText(file.getName());
                }
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

        // Right Side: Database Summary Viewer
        VBox rightPane = new VBox(15);
        rightPane.setPadding(new Insets(20));
        rightPane.setStyle("-fx-background-color: #ffffff;");
        
        browseTopicLabel = new Label("Topic: None");
        browseTopicLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        browseProbArea = createReadOnlyTextArea();
        browseMethArea = createReadOnlyTextArea();
        browseTakeArea = createReadOnlyTextArea();

        // NEW: BibTeX Export UI components
        Button exportBibtexBtn = new Button("Copy BibTeX Citation");
        exportBibtexBtn.setStyle("-fx-base: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        exportBibtexBtn.setDisable(true); // Disabled until a file is clicked
        Label clipboardStatus = new Label("");
        clipboardStatus.setStyle("-fx-text-fill: green; -fx-font-style: italic;");
        HBox exportBox = new HBox(10, exportBibtexBtn, clipboardStatus);
        exportBox.setAlignment(Pos.CENTER_LEFT);

        rightPane.getChildren().addAll(
            browseTopicLabel,
            createBoldLabel("Identified Problem:"), browseProbArea,
            createBoldLabel("Methodology:"), browseMethArea,
            createBoldLabel("Key Takeaway:"), browseTakeArea,
            exportBox
        );

        // Selection Listener
       // Selection Listener
        fileTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            clipboardStatus.setText(""); // Reset status on new selection
            
            if (newVal != null && newVal.getValue().isFile()) {
                File activeFile = newVal.getValue();
                PaperSummary summary = dbManager.getSummaryByPath(activeFile.getAbsolutePath());
                
                if (summary != null) {
                    browseTopicLabel.setText("Topic: " + summary.topic);
                    browseProbArea.setText(summary.problem);
                    browseMethArea.setText(summary.methodology);
                    browseTakeArea.setText(summary.takeaway);
                    exportBibtexBtn.setDisable(false);
                    
                    // NEW: Attach action to the export button for the current file
                    exportBibtexBtn.setOnAction(e -> {
                        String fileName = activeFile.getName().replaceFirst("[.][^.]+$", ""); // Remove extension
                        String citationKey = fileName.replaceAll("\\s+", "").replaceAll("[^a-zA-Z0-9]", "");
                        
                        // Construct the standard BibTeX format
                        String bibtex = String.format(
                            "@misc{%s,\n" +
                            "  title = {%s},\n" +
                            "  keywords = {%s},\n" +
                            "  note = {Problem: %s \\n Methodology: %s}\n" +
                            "}",
                            citationKey, fileName, summary.topic, summary.problem, summary.methodology
                        );
                        
                        Clipboard clipboard = Clipboard.getSystemClipboard();
                        ClipboardContent content = new ClipboardContent();
                        content.putString(bibtex);
                        clipboard.setContent(content);
                        
                        clipboardStatus.setText("Copied to clipboard!");
                    });
                    
                } else {
                    browseTopicLabel.setText("Status: Not processed or cached yet.");
                    browseProbArea.clear(); browseMethArea.clear(); browseTakeArea.clear();
                    exportBibtexBtn.setDisable(true);
                }
            } else {
                exportBibtexBtn.setDisable(true);
            }
        });

        fileTree.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<File> selectedItem = fileTree.getSelectionModel().getSelectedItem();
                if (selectedItem != null && selectedItem.getValue().isFile()) {
                    String fileName = selectedItem.getValue().getName().toLowerCase();
                    if (fileName.endsWith(".pdf")) {
                        openPdfViewer(selectedItem.getValue());
                    } else {
                        System.out.println("Viewer currently only supports .pdf files.");
                    }
                }
            }
        });

        splitPane.getItems().addAll(leftPane, rightPane);
        splitPane.setDividerPositions(0.30);

        VBox layout = new VBox(splitPane);
        return layout;
    }

    private void populateTree(File dir, TreeItem<File> parent) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                TreeItem<File> treeItem = new TreeItem<>(file);
                parent.getChildren().add(treeItem);
                if (file.isDirectory()) {
                    populateTree(file, treeItem);
                }
            }
        }
    }
    private void openPdfViewer(File pdfFile) {
        Stage viewerStage = new Stage();
        viewerStage.setTitle("Document Viewer: " + pdfFile.getName());

        ImageView imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(800);

        ScrollPane scrollPane = new ScrollPane(imageView);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #525659;"); 

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer renderer = new PDFRenderer(document);
            // Render the first page (index 0) at 150 DPI for clarity
            BufferedImage bufferedImage = renderer.renderImageWithDPI(0, 150);
            Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);
            imageView.setImage(fxImage);
        } catch (Exception e) {
            System.err.println("Failed to render PDF: " + e.getMessage());
            Label errorLabel = new Label("Could not load PDF: " + pdfFile.getName());
            scrollPane.setContent(errorLabel);
        }

        Scene scene = new Scene(scrollPane, 850, 900);
        viewerStage.setScene(scene);
        viewerStage.show();
    }
}