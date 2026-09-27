import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;

public class CacheBuster {
    public static void main(String[] args) {
        String targetFolder = "C:\\Users\\teja\\OneDrive\\Desktop\\MyResearchLibrary";
        String dbUrl = "jdbc:sqlite:papers.db";

        // Only grab the files currently sitting in the root folder
        List<File> pdfs = PDFExtractor.getPdfsInDirectory(targetFolder);
        
        if (pdfs.isEmpty()) {
            System.out.println("No PDFs found in the root folder to clear.");
            return;
        }

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            // Prepare SQL statements to delete specific rows based on the file hash
            PreparedStatement deleteCache = conn.prepareStatement("DELETE FROM processing_cache WHERE file_hash = ?");
            PreparedStatement deleteSummary = conn.prepareStatement("DELETE FROM summaries WHERE paper_id = (SELECT id FROM papers WHERE file_hash = ?)");
            PreparedStatement deletePaper = conn.prepareStatement("DELETE FROM papers WHERE file_hash = ?");
            
            for (File pdf : pdfs) {
                String hash = DatabaseManager.computeFileHash(pdf);
                if (hash != null) {
                    // 1. Delete from processing_cache
                    deleteCache.setString(1, hash);
                    deleteCache.executeUpdate();
                    
                    // 2. Delete from summaries
                    deleteSummary.setString(1, hash);
                    deleteSummary.executeUpdate();

                    // 3. Delete from papers
                    deletePaper.setString(1, hash);
                    deletePaper.executeUpdate();
                    
                    System.out.println("Targeted cache cleared for: " + pdf.getName());
                }
            }
            System.out.println("\nDone! Your database is safe. You can now run LLMOrchestrator to re-process these files.");
        } catch (Exception e) {
            System.err.println("Error clearing cache: " + e.getMessage());
        }
    }
}