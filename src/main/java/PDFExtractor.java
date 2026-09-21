import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.File;

public class PDFExtractor {
    
    public static String extractFirstPage(String filePath) {
        System.out.println("Reading PDF: " + filePath);
        
        try (PDDocument document = PDDocument.load(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            
            // Restrict extraction strictly to the first page to save LLM context window
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            
            String text = stripper.getText(document);
            
            if (text == null || text.trim().isEmpty()) {
                return "CORRUPTED_OR_UNREADABLE";
            }
            return text;
            
        } catch (Exception e) {
            System.err.println("Failed to read PDF: " + e.getMessage());
            return "CORRUPTED_OR_UNREADABLE"; // This safely triggers your LLM escape hatch
        }
    }
}