import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class SummarizerUI extends Application {

    private TextField urlInput;
    private Button startButton;
    private Label statusLabel;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Offline Research Paper Summarizer");

        // UI Components
        Label instructionLabel = new Label("Enter Local LLM URL:");
        urlInput = new TextField("http://127.0.0.1:8080/v1");
        startButton = new Button("Verify & Start Processing");
        statusLabel = new Label("Status: Ready.");

        // Layout Setup
        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.getChildren().addAll(instructionLabel, urlInput, startButton, statusLabel);

        // Button Action Hook
        startButton.setOnAction(e -> handleStartProcessing());

        Scene scene = new Scene(layout, 400, 250);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void handleStartProcessing() {
        String url = urlInput.getText();
        statusLabel.setText("Status: Verifying connection...");
        startButton.setDisable(true); 

        // Execute processing off the main UI thread to prevent freezing
        new Thread(() -> {
            boolean isConnected = LLMOrchestrator.verifyLLMConnection(url);

            if (isConnected) {
                // Safely update the JavaFX UI from the background thread
                Platform.runLater(() -> statusLabel.setText("Status: Connected! Processing library..."));
                
                String targetFolder = "C:\\Users\\teja\\OneDrive\\Desktop\\MyResearchLibrary";
                LLMOrchestrator.processLibrary(url, targetFolder);
                
                Platform.runLater(() -> {
                    statusLabel.setText("Status: Processing complete. Ready for review.");
                    startButton.setDisable(false);
                });
            } else {
                Platform.runLater(() -> {
                    statusLabel.setText("Status: Connection failed. Check server.");
                    startButton.setDisable(false);
                });
            }
        }).start();
    }
}