import java.util.List;

public class TopicManager {
    // Raju will eventually replace this mock data with a SQLite SELECT query
    public List<String> getActiveTopics() {
        return List.of("Machine Learning", "Computer Vision", "Distributed Systems");
    }

    public String formatTopicsForPrompt(List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return "No existing topics. Create a new, broad category.";
        }
        return String.join(", ", topics);
    }
}