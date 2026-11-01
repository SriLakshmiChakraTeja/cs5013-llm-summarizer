import java.util.List;

public class TopicManager {
    
    public String formatTopicsForPrompt(List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return "No existing topics. Create a new, broad category.";
        }
        return String.join(", ", topics);
    }
}