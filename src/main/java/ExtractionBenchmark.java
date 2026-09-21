import java.io.File;
import java.util.List;

public class ExtractionBenchmark {
    public static void main(String[] args) {
       String sampleFolder = "cs5013-llm-summarizer/sample_papers";
        System.out.println("=== Starting PDF Extraction Benchmark ===");

        List<File> pdfs = PDFExtractor.getPdfsInDirectory(sampleFolder);
        if (pdfs.isEmpty()) {
            System.err.println("No PDF files found in folder: " + sampleFolder);
            return;
        }

        System.out.println("Found " + pdfs.size() + " PDF(s) to benchmark.\n");

        for (File pdf : pdfs) {
            System.out.println("----------------------------------------");
            System.out.println("Testing file: " + pdf.getName());

            // 1. Test SHA-256 Hashing
            String hash = DatabaseManager.computeFileHash(pdf);
            System.out.println("Generated Hash: " + hash);

            // 2. Test Text Extraction with multi-column sorting
            String text = PDFExtractor.extractFirstPage(pdf.getAbsolutePath());
            if ("CORRUPTED_OR_UNREADABLE".equals(text)) {
                System.err.println("Status: FAILED (Corrupted or empty text)");
            } else {
                System.out.println("Status: SUCCESS");
                System.out.println("Extracted Length: " + text.length() + " characters");
                System.out.println("Preview (First 150 chars):\n" + text.substring(0, Math.min(text.length(), 150)).trim());
            }
            System.out.println("----------------------------------------\n");
        }

        System.out.println("=== Benchmark Completed Successfully ===");
    }
}