import java.util.*;

public class HeadlessRunner {
    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : "http://127.0.0.1:8080/v1";
        String folder = args.length > 1 ? args[1] : "/papers";

        if (!LLMOrchestrator.verifyLLMConnection(url)) {
            System.err.println("Cannot reach the local LLM server at " + url);
            System.exit(1);
        }

        List<FileOrganizer.MoveTask> tasks = Collections.synchronizedList(new ArrayList<>());
        LLMOrchestrator.processLibrary(url, folder,
            t -> { if (t.summary != null) tasks.add(t); },
            () -> {});

        DatabaseManager db = new DatabaseManager();
        List<String> existing = db.getAllTopics();
        Scanner in = new Scanner(System.in);

        System.out.println("\n=== Review: " + tasks.size() + " file(s) ===");
        for (FileOrganizer.MoveTask t : tasks) {
            System.out.println("\nFile: " + t.pdf.getName());
            System.out.println("Problem:     " + t.summary.problem);
            System.out.println("Methodology: " + t.summary.methodology);
            System.out.println("Takeaway:    " + t.summary.takeaway);
            System.out.println("Topics:      " + t.summary.topic);
            System.out.print("Enter Y to accept, N to skip, or type your own topics: ");
            String answer = in.nextLine().trim();
            if (answer.equalsIgnoreCase("N") || answer.isEmpty()) {
                System.out.println("Skipped (nothing saved).");
                continue;
            }
            String topics = answer.equalsIgnoreCase("Y") ? t.summary.topic : answer;
            FileOrganizer.executeMove(t, topics, folder, db, existing);
            System.out.println("Saved with topics: " + topics);
        }
        System.out.println("Done.");
    }
}