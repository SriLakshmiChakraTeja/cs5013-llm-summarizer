import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.File;

public class PDFExtractor {
    
    public static String extractFirstPage(String filePath) {
        try (PDDocument document = PDDocument.load(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            
            // Restoring the original 3-page limit
            stripper.setStartPage(1);
            stripper.setEndPage(3);
            
            String rawText = stripper.getText(document);
            
            if (rawText == null || rawText.trim().isEmpty()) {
                return "CORRUPTED_OR_UNREADABLE";
            }

            // MATH SANITIZER: Replace all non-standard English/punctuation characters
            String cleanText = rawText.replaceAll("[^a-zA-Z0-9\\s\\.,!\\?:;\\-\\(\\)\\[\\]\"']", " ");
            cleanText = cleanText.replaceAll("\\s+", " ").trim();
            return cleanText;

        } catch (Exception e) {
            System.err.println("Failed to read PDF: " + e.getMessage());
            return "CORRUPTED_OR_UNREADABLE"; 
        }
    }
    
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