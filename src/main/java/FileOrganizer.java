import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

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

    public static void executeMove(MoveTask task, String finalTopic, String libraryRoot, DatabaseManager dbManager, List<String> existingTopics) {
        File targetFolder = new File(libraryRoot, finalTopic);
        if (!targetFolder.exists()) {
            targetFolder.mkdirs(); 
        }

        Path sourcePath = Paths.get(task.pdf.getAbsolutePath());
        Path targetPath = Paths.get(targetFolder.getAbsolutePath(), sourcePath.getFileName().toString());

        try {
            task.summary.topic = finalTopic;
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            
            // Fixed: Save the new absolute path to the database, not the old root path
            dbManager.savePaperSummary(targetPath.toAbsolutePath().toString(), task.fileHash, task.summary);
            
            if (!existingTopics.contains(finalTopic)) {
                existingTopics.add(finalTopic);
            }
            System.out.println("-> Success: Moved to '" + targetFolder.getName() + "' and cached successfully.");
        } catch (Exception e) {
            System.err.println("-> Error moving file: " + e.getMessage());
        }
    }
}