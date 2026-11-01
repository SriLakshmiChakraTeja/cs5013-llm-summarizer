import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.sl.extractor.SlideShowExtractor;
import java.io.FileInputStream;

public class DocumentExtractor {

    public static String extractText(String filePath) {
        String lowerPath = filePath.toLowerCase();
        try {
            if (lowerPath.endsWith(".pdf")) {
                return PDFExtractor.extractFirstPage(filePath); 
            } else if (lowerPath.endsWith(".docx")) {
                return extractDocx(filePath);
            } else if (lowerPath.endsWith(".pptx")) {
                return extractPptx(filePath);
            } else if (lowerPath.endsWith(".md") || lowerPath.endsWith(".txt") || lowerPath.endsWith(".tex")) {
                return extractPlainText(filePath);
            }
        } catch (Exception e) {
            System.err.println("Failed to extract: " + e.getMessage());
        }
        return "CORRUPTED_OR_UNREADABLE"; 
    }

    private static String extractDocx(String filePath) throws Exception {
        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            return text.length() > 12000 ? text.substring(0, 12000) : text;
        }
    }

    private static String extractPptx(String filePath) throws Exception {
    try (FileInputStream fis = new FileInputStream(filePath);
         XMLSlideShow ppt = new XMLSlideShow(fis);
         SlideShowExtractor<?, ?> extractor = new SlideShowExtractor<>(ppt)) {
        extractor.setCommentsByDefault(false);
        extractor.setNotesByDefault(false);
        String text = extractor.getText();
        return text.length() > 12000 ? text.substring(0, 12000) : text;
    }
}

    private static String extractPlainText(String filePath) throws Exception {
        String text = Files.readString(new File(filePath).toPath());
        return text.length() > 12000 ? text.substring(0, 12000) : text;
    }

    public static List<File> getSupportedFilesInDirectory(String directoryPath) {
        List<File> supportedFiles = new ArrayList<>();
        File folder = new File(directoryPath);
        File[] listOfFiles = folder.listFiles();

        if (listOfFiles != null) {
            for (File file : listOfFiles) {
                if (file.isFile()) {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".pdf") || name.endsWith(".docx") || name.endsWith(".pptx") || 
                        name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".tex")) {
                        supportedFiles.add(file);
                    }
                }
            }
        }
        return supportedFiles;
    }
}