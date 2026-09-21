import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class LLMOrchestrator {
    
    public static void main(String[] args) {
        // 1. Initialize LangChain4j ChatModel (Module 4 Runtime)
        ChatLanguageModel model = OpenAiChatModel.builder()
            .baseUrl("http://127.0.0.1:8080/v1")
            .apiKey("not-needed") // Local offline models bypass authentication
            .modelName("g9v3")
            .temperature(0.0) // Force deterministic JSON output
            .build();

        // 2. Define the JSON schema prompt (Module 2 Summarization)
        String systemPrompt = "You are a research summarizer. Return ONLY a valid JSON object with the exact keys: 'problem', 'methodology', 'takeaway', and 'topic'. Do not include markdown formatting.";
        
        // 3. Inject active topics to prevent duplicate category tags (Module 3 Classification)
        String activeTopics = "[Machine Learning, Systems, Distributed Computing]";
        String pdfText = "This paper introduces a local inference system for research papers."; 
        String userPrompt = String.format("Available topics: %s.\n\nPaper text:\n%s", activeTopics, pdfText);

        System.out.println("Calling local llama.cpp server via LangChain4j...");

        // 4. Generate the response
        String rawResponse = model.generate(
            SystemMessage.from(systemPrompt),
            UserMessage.from(userPrompt)
        ).content().text();

        // 5. Clean the <think> blocks and markdown for Jackson/Gson parsing
        String cleanJson = rawResponse;
        if (cleanJson.contains("</think>")) {
            cleanJson = cleanJson.substring(cleanJson.lastIndexOf("</think>") + 8).trim();
        }
        cleanJson = cleanJson.replace("```json", "").replace("```", "").trim();
        
        System.out.println("\nClean JSON ready for Jackson/Gson Parsing:");
        System.out.println(cleanJson);
        System.exit(0);
    }
}