import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.File;

public class PDFExtractor {
    
    public static String extractFirstPage(String filePath) {
        try (PDDocument document = PDDocument.load(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            
            // Extract the first 3 pages to get enough context for slide decks and tutorials
            stripper.setStartPage(1);
            stripper.setEndPage(3);
            
            String rawText = stripper.getText(document);
            
            if (rawText == null || rawText.trim().isEmpty()) {
                return "CORRUPTED_OR_UNREADABLE";
            }

            // MATH SANITIZER: Replace all non-standard English/punctuation characters with a space
            // This strips out corrupted equations, integrals, and matrices, leaving the plain English words.
            String cleanText = rawText.replaceAll("[^a-zA-Z0-9\\s\\.,!\\?:;\\-\\(\\)\\[\\]\"']", " ");
            
            // // Collapse massive gaps left by deleted math equations into single spaces
            cleanText = cleanText.replaceAll("\\s+", " ").trim();
            return cleanText;
            // return rawText;

        } catch (Exception e) {
            System.err.println("Failed to read PDF: " + e.getMessage());
            return "CORRUPTED_OR_UNREADABLE"; 
        }
    }
    
    // Helper method for the Orchestrator to scan the directory
    public static java.util.List<File> getPdfsInDirectory(String directoryPath) {
        java.util.List<File> pdfFiles = new java.util.ArrayList<>();
        File folder = new File(directoryPath);
        File[] listOfFiles = folder.listFiles();

        if (listOfFiles != null) {
            for (File file : listOfFiles) {
                if (file.isFile() && file.getName().toLowerCase().endsWith(".pdf")) {
                    pdfFiles.add(file);
                }
            }
        }
        return pdfFiles;
    }
}