import java.util.Scanner;

public class DemoRunner {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   CS5013: Local LLM Research Summarizer Demo   ");
        System.out.println("==================================================");
        System.out.println("1. Run Full Processing Pipeline (Extract -> LLM -> Cache -> Organize)");
        System.out.println("2. Reset Cache for Testing (Run CacheBuster)");
        System.out.println("3. Exit");
        System.out.print("\nSelect an option to demonstrate: ");

        @SuppressWarnings("resource")
        Scanner scanner = new Scanner(System.in);
        String choice = scanner.nextLine().trim();

        try {
            if (choice.equals("1")) {
                LLMOrchestrator.main(new String[]{});
            } else if (choice.equals("2")) {
                CacheBuster.main(new String[]{});
            } else {
                System.out.println("Exiting demo.");
                System.exit(0);
            }
        } catch (Exception e) {
            System.err.println("Demo execution failed: " + e.getMessage());
        }
    }
}