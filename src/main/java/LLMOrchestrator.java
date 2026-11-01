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
                .timeout(Duration.ofMinutes(2)) 
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
            You are an expert document summarizer. Respond with ONE valid JSON object and nothing else.
            Use exactly these keys: "problem", "methodology", "takeaway", "topic".

            Rules:
            1. 'problem': Exactly 1 sentence (20-40 words). For a research paper, state the research area and the gap it addresses. For any other document (course material, report, schedule, notes), state its main purpose or context. Do not begin with "This document" or "The authors".
            2. 'methodology': One paragraph of 4-6 sentences. For a research paper, explain how the authors solve the problem. For any other document, summarize its tasks, instructions, or contents. Use the document's own terms.
            3. 'takeaway': Exactly 1 sentence (20-40 words). For a research paper, state the main result and conclusion. For any other document, state the final outcome, deliverable, or main point.
            4. 'topic': A short descriptive name (2-4 words, Title Case) that says what the document is specifically about, so a person reading the folder name knows what is inside.
                a) Name the specific subject matter: the technique, system, or problem the document deals with. Be more specific than a whole field. Too broad: "Software Engineering", "Machine Learning", "Computer Science". Good: the specific area within it.
                b) Do not make it so narrow that it only fits this one file. It should still suit a few related documents. Do not use author names, file names, version numbers, or the exact document title.
                c) Never use document-type words such as "Project", "Hands-on", "Handson", "Reflection", "Assignment", "Report", "Notes", "Document", "Module", "Course", "Demo", "Chat", "Schedule", "Plan", "Design Doc", or "General".
                d) Existing library topics: %s
                    Reuse one EXACTLY as written only if it describes the same specific subject as this document. A merely related broad field is not a match. If an existing topic is a document-type word or is too broad to describe this document, ignore it and write a new descriptive name.
                e) Use only letters, spaces, and hyphens.
                f) Format examples only. These are from unrelated subjects, so NEVER copy them: "Quantum Error Correction", "Kubernetes Autoscaling Strategies", "Graph Neural Network Pretraining", "Database Index Tuning", "Wireless Sensor Energy Saving".
               g) Use only letters, spaces, and hyphens.

            General rules:
            - Base every statement only on the provided text. Never invent results or numbers.
            - Plain sentences only: no bullets, markdown, or line breaks inside values.
            - Never output "N/A" for a document that contains readable sentences, even if it is not a research paper.
            - Output only the JSON object. No explanations, no reasoning text, no markdown fences.

            Only if the text is entirely random characters or symbols with no readable sentences, output exactly:
            {"problem": "Extraction failed due to corrupted text.", "methodology": "N/A", "takeaway": "N/A", "topic": "Uncategorized"}
            """;

        List<File> pdfs = DocumentExtractor.getSupportedFilesInDirectory(targetFolder);
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

            String rawPdfText = DocumentExtractor.extractText(pdf.getAbsolutePath());
            
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
        
                // STRICT EXTRACTION: Ignore any LLM preamble, cut-off thoughts, and isolate the JSON block
                int startIndex = cleanJson.indexOf('{');
                int endIndex = cleanJson.lastIndexOf('}');
                
                if (startIndex != -1 && endIndex != -1 && startIndex <= endIndex) {
                    cleanJson = cleanJson.substring(startIndex, endIndex + 1);
                } else {
                    // FALLBACK: If the model timed out before generating the '{', safely trigger the UI escape hatch
                    System.err.println("CRITICAL: LLM failed to generate a valid JSON structure.");
                    cleanJson = "{\n  \"problem\": \"Extraction failed: LLM output format error.\",\n  \"methodology\": \"N/A\",\n  \"takeaway\": \"N/A\",\n  \"topic\": \"Uncategorized\"\n}";
                }
                // System.out.println("RAW LLM OUTPUT:\n" + rawResponse);
                
                System.out.println("\nClean JSON ready for Jackson/Gson Parsing:");
                System.out.println(cleanJson);

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