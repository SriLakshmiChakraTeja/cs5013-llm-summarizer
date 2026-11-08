import java.io.File;
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

    public static void executeMove(MoveTask task, String finalTopics, String libraryRoot, DatabaseManager dbManager, List<String> existingTopics) {
        try {
            task.summary.topic = finalTopics;
            
            dbManager.savePaperSummary(task.pdf.getAbsolutePath(), task.fileHash, task.summary);
            
            String[] splitTopics = finalTopics.split(",");
            for (String t : splitTopics) {
                String cleanTopic = t.trim();
                if (!existingTopics.contains(cleanTopic)) {
                    existingTopics.add(cleanTopic);
                }
            }
            System.out.println("-> Success: Cached logically with topics: '" + finalTopics + "'. File remained in root.");
        } catch (Exception e) {
            System.err.println("-> Error caching file metadata: " + e.getMessage());
        }
    }
}