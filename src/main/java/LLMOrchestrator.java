import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class LLMOrchestrator {
    private static final String MODEL_NAME = "g9v3";
    
    public static boolean verifyLLMConnection(String inputUrl) {
        try {
            String modelUrl = normalizeModelUrl(inputUrl);
            System.out.println("Checking LLM server at '" + modelUrl + "'...");
            
            ChatLanguageModel testModel = OpenAiChatModel.builder()
                .baseUrl(modelUrl)
                .apiKey("not-needed")
                .modelName(MODEL_NAME)
                .temperature(0.0)
                .timeout(Duration.ofSeconds(5)) 
                .build();

            String response = testModel.generate("Reply with exactly: OK");
            boolean success = response != null && !response.isBlank();
            if (success) System.out.println("LLM server is reachable.");
            return success;
        } catch (Exception e) {
            System.err.println("Verification failed: " + e.getMessage());
            return false;
        }
    }

    public static List<FileOrganizer.MoveTask> processLibrary(String verifiedModelUrl, String targetFolder) {
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
        java.util.logging.LogManager.getLogManager().reset();

        ChatLanguageModel model = OpenAiChatModel.builder()
            .baseUrl(verifiedModelUrl)
            .apiKey("not-needed")
            .modelName(MODEL_NAME)
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

        List<File> pdfs = PDFExtractor.getPdfsInDirectory(targetFolder);
        List<FileOrganizer.MoveTask> pendingMoves = new ArrayList<>();
        
        if (pdfs.isEmpty()) {
            System.out.println("No PDFs found in " + targetFolder);
            return pendingMoves; 
        }

        System.out.println("Found " + pdfs.size() + " PDF(s). Starting processing loop...\n");

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

                String cleanJson = rawResponse;
                int jsonStart = cleanJson.indexOf('{');
                int jsonEnd = cleanJson.lastIndexOf('}');
                
                if (jsonStart != -1 && jsonEnd != -1 && jsonStart < jsonEnd) {
                    cleanJson = cleanJson.substring(jsonStart, jsonEnd + 1);
                } else {
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
        
        return pendingMoves;
    }

    private static String normalizeModelUrl(String input) {
        String modelUrl = input.trim();
        if (modelUrl.isEmpty()) throw new IllegalArgumentException("LLM model URL cannot be empty.");
        if (!modelUrl.contains("://")) modelUrl = "http://" + modelUrl;

        URI parsedModelUrl;
        try {
            parsedModelUrl = URI.create(modelUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid LLM model URL: " + modelUrl, e);
        }

        String scheme = parsedModelUrl.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("LLM model URL must use HTTP or HTTPS.");
        }
        if (parsedModelUrl.getHost() == null) {
            throw new IllegalArgumentException("LLM model URL must include a valid host.");
        }

        if (!isLocalHost(parsedModelUrl.getHost())) {
            throw new IllegalArgumentException(
                "LLM model URL must point to a local server (localhost, 127.x.x.x, or ::1)."
            );
        }

        return modelUrl.replaceAll("/+$", "");
    }

    private static boolean isLocalHost(String host) {
        String normalizedHost = host.toLowerCase();
        if ("localhost".equals(normalizedHost) || "::1".equals(normalizedHost)
                || "[::1]".equals(normalizedHost)) {
            return true;
        }

        String[] octets = normalizedHost.split("\\.", -1);
        if (octets.length != 4 || !"127".equals(octets[0])) {
            return false;
        }
        for (String octet : octets) {
            try {
                int value = Integer.parseInt(octet);
                if (value < 0 || value > 255 || !Integer.toString(value).equals(octet)) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }
}