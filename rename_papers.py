import sqlite3
import os
import re

DB_PATH = "papers.db"

def sanitize_filename(name):
    """Removes illegal characters from the topic string to make it safe for file systems."""
    return re.sub(r'[\\/*?:"<>|]', "", name)

def rename_papers():
    if not os.path.exists(DB_PATH):
        print(f"Database not found at {DB_PATH}. Please run the script in your project root.")
        return

    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    # Fetch all processed papers and their assigned topics
    cursor.execute("SELECT p.id, p.file_path, s.topic FROM papers p JOIN summaries s ON p.id = s.paper_id")
    records = cursor.fetchall()
    
    renamed_count = 0
    for paper_id, file_path, topics in records:
        if not os.path.exists(file_path):
            print(f"File not found on disk, skipping: {file_path}")
            continue
            
        # Extract the primary topic (the first one before any commas)
        primary_topic = topics.split(',')[0].strip()
        if not primary_topic or primary_topic == "Uncategorized":
            continue
            
        # Clean the topic string to use as a prefix
        safe_topic_prefix = sanitize_filename(primary_topic).replace(" ", "_")
        
        dir_name = os.path.dirname(file_path)
        old_file_name = os.path.basename(file_path)
        
        # Skip if it has already been renamed
        if old_file_name.startswith(safe_topic_prefix):
            continue
            
        # Construct the new file name: e.g., "Machine_Learning_2611.00960v1.pdf"
        new_file_name = f"{safe_topic_prefix}_{old_file_name}"
        new_file_path = os.path.join(dir_name, new_file_name)
        
        try:
            # 1. Rename the physical file on disk
            os.rename(file_path, new_file_path)
            
            # 2. Update the SQLite database with the new absolute path
            cursor.execute("UPDATE papers SET file_path = ? WHERE id = ?", (new_file_path, paper_id))
            
            print(f"Renamed: {old_file_name}  ->  {new_file_name}")
            renamed_count += 1
        except Exception as e:
            print(f"Error renaming {old_file_name}: {e}")
            
    conn.commit()
    conn.close()
    print(f"\nSuccessfully renamed {renamed_count} file(s) and synced the database.")

if __name__ == "__main__":
    rename_papers()