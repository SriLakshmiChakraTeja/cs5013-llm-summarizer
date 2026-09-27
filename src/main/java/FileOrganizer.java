import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Scanner;

public class FileOrganizer {

    public static void categorizeAndMove(String sourceFilePath, String topic, String libraryRoot) {
        if (topic == null || topic.equals("Uncategorized") || topic.contains("Extraction failed")) {
            System.out.println("Skipping automated move for uncategorized or corrupted file.");
            return;
        }

        File targetFolder = new File(libraryRoot, topic);
        if (!targetFolder.exists()) {
            targetFolder.mkdirs(); 
        }

        Path sourcePath = Paths.get(sourceFilePath);
        Path targetPath = Paths.get(targetFolder.getAbsolutePath(), sourcePath.getFileName().toString());

        System.out.println("\nReady to organize:");
        System.out.println("Move '" + sourcePath.getFileName() + "' to '" + targetFolder.getAbsolutePath() + "'?");
        System.out.print("Enter Y to confirm or N to skip: ");
        
        Scanner scanner = new Scanner(System.in);
        String response = scanner.nextLine().trim().toUpperCase();

        if (response.equals("Y")) {
            try {
                Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Success: File moved to " + targetPath.toString());
            } catch (Exception e) {
                System.err.println("Error moving file: " + e.getMessage());
            }
        } else {
            System.out.println("Move cancelled by user.");
        }
    }
}