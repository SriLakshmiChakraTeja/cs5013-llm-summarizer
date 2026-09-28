import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LLMOrchestrator {
    
    public static void main(String[] args) {
        // 1. BULLETPROOF LOGGER SUPPRESSION
        // Disable Apache Commons Logging (used by PDFBox)
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
        // Disable Java Util Logging (JUL) entirely for this CLI run to silence font warnings
        java.util.logging.LogManager.getLogManager().reset();

        ChatLanguageModel model = OpenAiChatModel.builder()
            .baseUrl("http://127.0.0.1:8080/v1")
            .apiKey("not-needed")
            .modelName("g9v3")
            .temperature(0.0)
            .timeout(Duration.ofMinutes(5)) 
            .build();

        DatabaseManager dbManager = new DatabaseManager();
        TopicManager topicManager = new TopicManager();
        ObjectMapper mapper = new ObjectMapper();

        String systemPrompt = """
            You are an academic document summarizer. Read the provided text and return ONLY a valid JSON object.
            
            Rules for Summarization:
            1. 'problem': Identify the overall subject of the document. If it is a research paper, summarize the core problem. If it is a tutorial, slide deck, or textbook chapter, summarize the overarching topics covered (e.g., 'A tutorial covering linear transformations and vector spaces'). Do NOT just copy the first question.
            2. 'methodology': Summarize the format or approach (e.g., 'Problem set with solutions', 'Theoretical proofs and definitions', 'Software documentation') in 1-2 sentences.
            3. 'takeaway': Summarize the main learning outcome or conclusion in 1 sentence.
            4. 'topic': Categorize the paper using ONLY ONE of the 'Available topics' provided in the user message.
            
            Rules for Corrupted Text:
            If the provided text contains NO readable English words, DO NOT invent a summary. 
            Instead, you MUST output exactly this:
            {
              "problem": "Extraction failed due to corrupted text.",
              "methodology": "N/A",
              "takeaway": "N/A",
              "topic": "Uncategorized"
            }
            
            Do not output markdown formatting or <think> tags.
            """;

        String targetFolder = "C:\\Users\\teja\\OneDrive\\Desktop\\MyResearchLibrary";
        List<File> pdfs = PDFExtractor.getPdfsInDirectory(targetFolder);
        
        if (pdfs.isEmpty()) {
            System.out.println("No PDFs found in " + targetFolder);
            System.exit(0);
        }

        System.out.println("Found " + pdfs.size() + " PDF(s). Starting processing loop...\n");

        List<FileOrganizer.MoveTask> pendingMoves = new ArrayList<>();

        for (File pdf : pdfs) {
            System.out.println("--------------------------------------------------");
            System.out.println("Processing: " + pdf.getName());

            String fileHash = DatabaseManager.computeFileHash(pdf);
            if (fileHash != null && dbManager.isCached(fileHash)) {
                System.out.println("Skipping: Already processed and cached.");
                continue; 
            }

            String rawPdfText = PDFExtractor.extractFirstPage(pdf.getAbsolutePath());
            
            String pdfText = rawPdfText.length() > 12000 
                ? rawPdfText.substring(0, 12000) 
                : rawPdfText;

            List<String> activeTopics = dbManager.getAllTopics();
            String formattedTopics = topicManager.formatTopicsForPrompt(activeTopics);
            
            String userPrompt = String.format("Available topics: [%s].\n\nPaper text:\n%s", formattedTopics, pdfText);

            System.out.println("Calling local llama.cpp server via LangChain4j...");

            try {
                String rawResponse = model.generate(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userPrompt)
                ).content().text();

                // STRICT BOUNDARY EXTRACTOR: Slice out only the JSON object
                String cleanJson = rawResponse;
                int jsonStart = cleanJson.indexOf('{');
                int jsonEnd = cleanJson.lastIndexOf('}');
                
                if (jsonStart != -1 && jsonEnd != -1 && jsonStart < jsonEnd) {
                    cleanJson = cleanJson.substring(jsonStart, jsonEnd + 1);
                } else {
                    // LLM completely failed to generate JSON. Trigger the escape hatch manually.
                    cleanJson = "{\"problem\": \"Extraction failed: LLM output format error.\", \"methodology\": \"N/A\", \"takeaway\": \"N/A\", \"topic\": \"Uncategorized\"}";
                }
                
                System.out.println("Clean JSON generated successfully.");

                PaperSummary summary = mapper.readValue(cleanJson, PaperSummary.class);
                pendingMoves.add(new FileOrganizer.MoveTask(pdf, summary, fileHash));

            } catch (Exception e) {
                System.err.println("Error processing file " + pdf.getName() + ": " + e.getMessage());
            }
        }
        
        System.out.println("--------------------------------------------------");
        System.out.println("All files processed.");
        
        List<String> finalTopics = dbManager.getAllTopics();
        FileOrganizer.reviewAndMoveBulk(pendingMoves, targetFolder, finalTopics, dbManager);
        
        System.exit(0);
    }
}