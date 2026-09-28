import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Scanner;

public class CacheBuster {
    public static void main(String[] args) {
        String targetFolder = "C:\\Users\\teja\\OneDrive\\Desktop\\MyResearchLibrary";
        String dbUrl = "jdbc:sqlite:papers.db";
        DatabaseManager dbManager = new DatabaseManager();

        List<File> pdfs = PDFExtractor.getPdfsInDirectory(targetFolder);
        
        if (pdfs.isEmpty()) {
            System.out.println("No PDFs found in the root folder.");
            return;
        }

        System.out.println("\n==================================================");
        System.out.println("   CACHE MANAGER: SELECT FILES TO RESET   ");
        System.out.println("==================================================");
        
        for (int i = 0; i < pdfs.size(); i++) {
            File pdf = pdfs.get(i);
            String hash = DatabaseManager.computeFileHash(pdf);
            String status = dbManager.isCached(hash) ? "[CACHED]" : "[NOT CACHED]";
            System.out.println("[" + (i + 1) + "] " + status + " " + pdf.getName());
        }

        System.out.println("[0] CANCEL and Exit");
        System.out.println("[A] Clear ALL Cached Files Listed Above");
        // NEW: Instructions for multi-select
        System.out.print("\nEnter numbers separated by spaces (e.g., 1 3 5), or 'A' for all: ");

        @SuppressWarnings("resource")
        Scanner scanner = new Scanner(System.in);
        String choice = scanner.nextLine().trim().toUpperCase();

        if (choice.equals("0") || choice.isEmpty()) {
            System.out.println("Operation cancelled.");
            return;
        }

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            PreparedStatement deleteCache = conn.prepareStatement("DELETE FROM processing_cache WHERE file_hash = ?");
            PreparedStatement deleteSummary = conn.prepareStatement("DELETE FROM summaries WHERE paper_id = (SELECT id FROM papers WHERE file_hash = ?)");
            PreparedStatement deletePaper = conn.prepareStatement("DELETE FROM papers WHERE file_hash = ?");

            if (choice.equals("A")) {
                for (File pdf : pdfs) {
                    clearFileCache(pdf, deleteCache, deleteSummary, deletePaper);
                }
                System.out.println("\nDone! Cache cleared for all listed files.");
            } else {
                // NEW: Split the input by spaces or commas to handle multiple numbers
                String[] selections = choice.split("[,\\s]+");
                System.out.println();
                
                for (String sel : selections) {
                    try {
                        int index = Integer.parseInt(sel) - 1;
                        if (index >= 0 && index < pdfs.size()) {
                            clearFileCache(pdfs.get(index), deleteCache, deleteSummary, deletePaper);
                            System.out.println("Cleared cache for: " + pdfs.get(index).getName());
                        } else {
                            System.out.println("Ignored invalid number: " + sel);
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Ignored invalid input: " + sel);
                    }
                }
                System.out.println("\nSelected cache clearing complete.");
            }
        } catch (Exception e) {
            System.err.println("Error clearing cache: " + e.getMessage());
        }
    }

    private static void clearFileCache(File pdf, PreparedStatement deleteCache, PreparedStatement deleteSummary, PreparedStatement deletePaper) throws Exception {
        String hash = DatabaseManager.computeFileHash(pdf);
        if (hash != null) {
            deleteCache.setString(1, hash);
            deleteCache.executeUpdate();
            
            deleteSummary.setString(1, hash);
            deleteSummary.executeUpdate();
            
            deletePaper.setString(1, hash);
            deletePaper.executeUpdate();
        }
    }
}