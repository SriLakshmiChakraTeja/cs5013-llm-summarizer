import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class FileOrganizer {

    public static class MoveTask {
        public File pdf;
        public PaperSummary summary;
        public String fileHash;
        
        public MoveTask(File pdf, PaperSummary summary, String fileHash) {
            this.pdf = pdf;
            this.summary = summary;
            this.fileHash = fileHash;
        }
    }

    public static void reviewAndMoveBulk(List<MoveTask> tasks, String libraryRoot, List<String> existingTopics, DatabaseManager dbManager) {
        if (tasks.isEmpty()) {
            System.out.println("No new files pending organization.");
            return;
        }

        System.out.println("\n==================================================");
        System.out.println("   FILE ORGANIZATION: INDIVIDUAL REVIEW   ");
        System.out.println("==================================================");
        
        @SuppressWarnings("resource")
        Scanner scanner = new Scanner(System.in);
        boolean hasValidMoves = false;

        for (MoveTask task : tasks) {
            if (task.summary.topic == null || task.summary.topic.equals("Uncategorized") || task.summary.topic.contains("Extraction failed")) {
                System.out.println("\nFile   : " + task.pdf.getName());
                System.out.println("Status : -> SKIPPED AUTOMATICALLY (Text extraction failed or LLM flagged as Uncategorized)");
                continue; 
            }
            
            hasValidMoves = true;
            System.out.println("\nFile    : " + task.pdf.getName());
            // NEW: Print all three summary fields for richer context
            System.out.println("Problem : " + task.summary.problem);
            System.out.println("Method  : " + task.summary.methodology);
            System.out.println("Outcome : " + task.summary.takeaway);
            
            List<String> options = new ArrayList<>();
            options.add(task.summary.topic); 
            
            for (String topic : existingTopics) {
                if (!topic.equalsIgnoreCase(task.summary.topic)) {
                    options.add(topic);
                }
            }

            System.out.println("\nOptions:");
            System.out.println("[0] SKIP file (Will not be cached or moved)");
            System.out.println("[1] " + options.get(0) + " (LLM Suggestion)");
            for (int i = 1; i < options.size(); i++) {
                System.out.println("[" + (i + 1) + "] " + options.get(i));
            }
            
            System.out.print("\nEnter number to select, or type a completely new folder name: ");
            String response = scanner.nextLine().trim();
            
            String finalTopic;
            if (response.isEmpty()) {
                finalTopic = options.get(0); 
            } else {
                try {
                    int choice = Integer.parseInt(response);
                    if (choice == 0) {
                        System.out.println("-> Skipped. File remains in root folder and was NOT cached.");
                        continue;
                    } else if (choice > 0 && choice <= options.size()) {
                        finalTopic = options.get(choice - 1);
                    } else {
                        System.out.println("-> Invalid number. Skipping file.");
                        continue;
                    }
                } catch (NumberFormatException e) {
                    finalTopic = response;
                }
            }

            File targetFolder = new File(libraryRoot, finalTopic);
            if (!targetFolder.exists()) {
                targetFolder.mkdirs(); 
            }

            Path sourcePath = Paths.get(task.pdf.getAbsolutePath());
            Path targetPath = Paths.get(targetFolder.getAbsolutePath(), sourcePath.getFileName().toString());

            try {
                task.summary.topic = finalTopic;
                Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                dbManager.savePaperSummary(task.pdf.getAbsolutePath(), task.fileHash, task.summary);
                
                System.out.println("-> Success: Moved to '" + targetFolder.getName() + "' and cached successfully.");
                
                if (!existingTopics.contains(finalTopic)) {
                    existingTopics.add(finalTopic);
                }
            } catch (Exception e) {
                System.err.println("-> Error moving file: " + e.getMessage());
            }
        }

        if (!hasValidMoves) {
            System.out.println("\nAll processed files were marked as corrupted or uncategorized. No moves to execute.");
        } else {
            System.out.println("\nOrganization review complete.");
        }
    }
}