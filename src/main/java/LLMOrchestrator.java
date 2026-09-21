import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.time.Duration; // <-- Must be at the top with the other imports

public class LLMOrchestrator {
    
    public static void main(String[] args) {
        // 1. Initialize LangChain4j ChatModel with Timeout
        ChatLanguageModel model = OpenAiChatModel.builder()
            .baseUrl("http://127.0.0.1:8080/v1")
            .apiKey("not-needed")
            .modelName("g9v3")
            .temperature(0.0)
            .timeout(Duration.ofMinutes(5)) // <-- Extended timeout for local hardware
            .build();

        // 2. Define the hardened JSON schema prompt
        String systemPrompt = """
            You are an academic research summarizer. Read the provided text and return ONLY a valid JSON object.
            
            Rules for Summarization:
            1. 'problem': Summarize the core issue the paper addresses in 1-2 concise sentences.
            2. 'methodology': Summarize the authors' proposed solution or architecture in 1-2 sentences.
            3. 'takeaway': Summarize the main result or conclusion in 1 sentence.
            4. 'topic': Categorize the paper using ONLY ONE of the 'Available topics' provided in the user message.
            
            Rules for Corrupted Text:
            If the provided text is a string of random characters, mathematical symbols, or completely unreadable due to PDF extraction errors, DO NOT invent a summary. 
            Instead, you MUST output exactly this:
            {
              "problem": "Extraction failed due to corrupted text.",
              "methodology": "N/A",
              "takeaway": "N/A",
              "topic": "Uncategorized"
            }
            
            Do not output markdown formatting or <think> tags.
            """;
        
        // 3. Extract text from your real PDF file
        String filePath = "C:\\Users\\teja\\OneDrive\\Desktop\\PWI\\handson1.pdf"; 
        String rawPdfText = PDFExtractor.extractFirstPage(filePath);
        
        // Truncate to protect the 4,096 token context window
        String pdfText = rawPdfText.length() > 12000 
            ? rawPdfText.substring(0, 12000) 
            : rawPdfText;

        String activeTopics = "[Machine Learning, Systems, Distributed Computing, Artificial Intelligence]";
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
        
        // Cleanly terminate the background network threads
        System.exit(0);
    }
}