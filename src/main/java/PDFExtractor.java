import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PDFExtractor {

    // Directory se saari PDF files scan karne ke liye
    public static List<File> getPdfsInDirectory(String directoryPath) {
        List<File> pdfFiles = new ArrayList<>();
        File dir = new File(directoryPath);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf"));
            if (files != null) {
                for (File f : files) {
                    pdfFiles.add(f);
                }
            }
        }
        return pdfFiles;
    }

    public static String extractFirstPage(String filePath) {
        System.out.println("Reading PDF: " + filePath);
        
        try (PDDocument document = PDDocument.load(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            
            // Multi-column IEEE layout ke liye position sort enable kiya
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            
            String text = stripper.getText(document);
            
            if (text == null || text.trim().isEmpty()) {
                return "CORRUPTED_OR_UNREADABLE";
            }
            return text;
            
        } catch (Exception e) {
            System.err.println("Failed to read PDF: " + e.getMessage());
            return "CORRUPTED_OR_UNREADABLE";
        }
    }
}