import json
import time
from pathlib import Path
from openai import OpenAI
import sqlite3

def main():
    client = OpenAI()

    my_position = Path(__file__).resolve().parent
    input_files = my_position / "paraphrase" / "clarus_high.txt"
    output_folder = my_position / "db_embeddings"
    output_folder.mkdir(parents=True, exist_ok=True)
    database = output_folder / "db_embedded_chunk_context.db"
    con = sqlite3.connect(str(database))
    cur = con.cursor()
    cur.execute("CREATE TABLE IF NOT EXISTS chunks(input_file, text, vector)")

    DOCUMENTS = [
        input_files
    ]

    for doc in DOCUMENTS:
        context_chunks = []
        with open(doc) as f:
            doc_as_string = f.read()
            chunk_size = 700
            overlap = 30
            point_in_doc = 0

            while point_in_doc < len(doc_as_string):
                end_point_in_doc = point_in_doc + chunk_size
                context_chunks.append(doc_as_string[point_in_doc:end_point_in_doc])
                point_in_doc = end_point_in_doc - overlap
            
            for chunk in context_chunks:
                response = client.embeddings.create(
                    input=chunk,
                    model="text-embedding-3-small"
                )
                embeddings_file = json.dumps(response.data[0].embedding)
                cur.execute("""
                    INSERT INTO chunks (input_file, text, vector) VALUES (?,?,?)
                    """,
                    (str(doc),chunk,embeddings_file)
                )
                con.commit()
                    
            f.close()
    con.close()

if __name__ == "__main__":
    main()