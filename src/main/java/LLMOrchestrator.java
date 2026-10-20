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
            You are an expert academic research summarizer. Read the provided text and return ONLY a valid JSON object.
            
            Rules for Summarization:
            1. 'problem': Write a paragraph (1 sentence) explaining the background, the context of the field, and the exact core issue or gap the paper addresses.
            2. 'methodology': Write a detailed paragraph (4-6 sentences) thoroughly explaining the authors' proposed solution, system architecture, mathematical models, or experimental setup.
            3. 'takeaway': Write a paragraph (1 sentence) expanding on the main results, specific performance improvements, conclusions, and future implications.
            4. 'topic': You MUST strictly evaluate the 'Available topics' provided in the user message. Prioritize matching the paper to an existing topic at all costs. ONLY invent a new 1-3 word topic if the paper's subject matter is completely alien to EVERY available option.
            
            Rules for Corrupted Text:
            If the provided text is a string of random characters, mathematical symbols, or completely unreadable due to PDF extraction errors, DO NOT invent a summary. 
            Instead, you MUST output exactly this:
            {
              "problem": "Extraction failed due to corrupted text.",
              "methodology": "N/A",
              "takeaway": "N/A",
              "topic": "Uncategorized"
            }
            
            Ensure all output is properly escaped for valid JSON. Do not output markdown formatting or <think> tags.
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