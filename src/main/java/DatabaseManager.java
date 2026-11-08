import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:papers.db";

    public DatabaseManager() {
        initDatabase();
    }

    private void initDatabase() {
        String sqlCreatePapers = """
            CREATE TABLE IF NOT EXISTS papers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                file_path TEXT NOT NULL,
                file_hash TEXT UNIQUE NOT NULL
            );
            """;

        String sqlCreateSummaries = """
            CREATE TABLE IF NOT EXISTS summaries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                paper_id INTEGER,
                problem TEXT,
                methodology TEXT,
                takeaway TEXT,
                topic TEXT,
                notes TEXT,
                FOREIGN KEY (paper_id) REFERENCES papers (id)
            );
            """;

        String sqlCreateTopics = """
            CREATE TABLE IF NOT EXISTS topics (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE NOT NULL
            );
            """;

        String sqlCreateCache = """
            CREATE TABLE IF NOT EXISTS processing_cache (
                file_hash TEXT PRIMARY KEY,
                paper_id INTEGER,
                processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (paper_id) REFERENCES papers (id)
            );
            """;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sqlCreatePapers);
            stmt.execute(sqlCreateSummaries);
            stmt.execute(sqlCreateTopics);
            stmt.execute(sqlCreateCache);
        } catch (SQLException e) {
            System.err.println("Database initialization error: " + e.getMessage());
        }
    }

    public static String computeFileHash(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesCount;
            while ((bytesCount = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesCount);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            System.err.println("Error generating SHA-256 hash: " + e.getMessage());
            return null;
        }
    }

    public PaperSummary getSummaryByPath(String filePath) {
        String query = "SELECT s.problem, s.methodology, s.takeaway, s.topic, s.notes " +
                       "FROM summaries s JOIN papers p ON s.paper_id = p.id " +
                       "WHERE p.file_path = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, filePath);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                PaperSummary summary = new PaperSummary();
                summary.problem = rs.getString("problem");
                summary.methodology = rs.getString("methodology");
                summary.takeaway = rs.getString("takeaway");
                summary.topic = rs.getString("topic");
                summary.notes = rs.getString("notes") == null ? "" : rs.getString("notes");
                return summary;
            }
        } catch (SQLException e) {
            System.err.println("Error fetching summary: " + e.getMessage());
        }
        return null;
    }

    public boolean isCached(String fileHash) {
        String query = "SELECT file_hash FROM processing_cache WHERE file_hash = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, fileHash);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.err.println("Cache query error: " + e.getMessage());
            return false;
        }
    }

    public void savePaperSummary(String filePath, String fileHash, PaperSummary summary) {
        String insertPaper = "INSERT INTO papers(file_path, file_hash) VALUES(?, ?)";
        String insertSummary = "INSERT INTO summaries(paper_id, problem, methodology, takeaway, topic, notes) VALUES(?, ?, ?, ?, ?, ?)";
        String insertTopic = "INSERT OR IGNORE INTO topics(name) VALUES(?)";
        String insertCache = "INSERT OR IGNORE INTO processing_cache(file_hash, paper_id) VALUES(?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);

            int paperId = -1;
            try (PreparedStatement pstmt = conn.prepareStatement(insertPaper, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, filePath);
                pstmt.setString(2, fileHash);
                pstmt.executeUpdate();
                ResultSet rs = pstmt.getGeneratedKeys();
                if (rs.next()) {
                    paperId = rs.getInt(1);
                }
            }

            try (PreparedStatement pstmt = conn.prepareStatement(insertSummary)) {
                pstmt.setInt(1, paperId);
                pstmt.setString(2, summary.problem);
                pstmt.setString(3, summary.methodology);
                pstmt.setString(4, summary.takeaway);
                pstmt.setString(5, summary.topic);
                pstmt.setString(6, summary.notes);
                pstmt.executeUpdate();
            }

            // Split comma-separated topics and insert them individually for UI dropdowns
            String[] splitTopics = summary.topic.split(",");
            try (PreparedStatement pstmt = conn.prepareStatement(insertTopic)) {
                for (String t : splitTopics) {
                    pstmt.setString(1, t.trim());
                    pstmt.executeUpdate();
                }
            }

            try (PreparedStatement pstmt = conn.prepareStatement(insertCache)) {
                pstmt.setString(1, fileHash);
                pstmt.setInt(2, paperId);
                pstmt.executeUpdate();
            }

            conn.commit();
            System.out.println("Saved metadata to SQLite: " + filePath);
        } catch (SQLException e) {
            System.err.println("Failed to persist metadata: " + e.getMessage());
        }
    }
    
    // NEW METHOD: Enables seamless reassignment and note updates from the UI
    public void updatePaperMetadata(String filePath, String newTopics, String newNotes) {
        String query = "UPDATE summaries SET topic = ?, notes = ? " +
                       "WHERE paper_id = (SELECT id FROM papers WHERE file_path = ?)";
        String insertTopic = "INSERT OR IGNORE INTO topics(name) VALUES(?)";

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, newTopics);
                pstmt.setString(2, newNotes);
                pstmt.setString(3, filePath);
                pstmt.executeUpdate();
            }
            
            // Ensure any newly added topics are registered in the global vocabulary
            String[] splitTopics = newTopics.split(",");
            try (PreparedStatement pstmt = conn.prepareStatement(insertTopic)) {
                for (String t : splitTopics) {
                    pstmt.setString(1, t.trim());
                    pstmt.executeUpdate();
                }
            }
            
            conn.commit();
        } catch (SQLException e) {
            System.err.println("Failed to update metadata: " + e.getMessage());
        }
    }

    public List<String> getAllTopics() {
        List<String> topics = new ArrayList<>();
        String query = "SELECT name FROM topics";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                topics.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch topics: " + e.getMessage());
        }
        return topics;
    }
}