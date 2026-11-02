package tech.kayys.cifr.engine.index.search;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@ApplicationScoped
public class LuceneTextSearchProvider implements TextSearchProvider {

    private Directory memoryIndex;

    @Override
    public Uni<Void> initialize(Path workspaceRoot) {
        return Uni.createFrom().item(() -> {
            try {
                memoryIndex = new ByteBuffersDirectory();
                StandardAnalyzer analyzer = new StandardAnalyzer();
                IndexWriterConfig config = new IndexWriterConfig(analyzer);
                
                try (IndexWriter writer = new IndexWriter(memoryIndex, config)) {
                    try (Stream<Path> paths = Files.walk(workspaceRoot)) {
                        paths.filter(Files::isRegularFile)
                             .filter(this::shouldInclude)
                             .forEach(path -> indexFile(writer, path));
                    }
                }
                return null;
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize Lucene index", e);
            }
        });
    }

    private void indexFile(IndexWriter writer, Path path) {
        try {
            String content = Files.readString(path);
            Document doc = new Document();
            doc.add(new StringField("path", path.toString(), Field.Store.YES));
            doc.add(new TextField("content", content, Field.Store.YES));
            writer.addDocument(doc);
        } catch (IOException e) {
            // Ignore unreadable files
        }
    }

    private boolean shouldInclude(Path path) {
        String pStr = path.toString();
        return !pStr.contains("/.git/") && !pStr.contains("/.idea/") && !pStr.contains("/target/") && !pStr.contains("/node_modules/");
    }

    @Override
    public Uni<SearchResult> search(SearchRequest request, Path workspaceRoot) {
        return Uni.createFrom().item(() -> {
            if (memoryIndex == null) {
                throw new IllegalStateException("Lucene index not initialized");
            }
            try {
                StandardAnalyzer analyzer = new StandardAnalyzer();
                QueryParser parser = new QueryParser("content", analyzer);
                Query query = parser.parse(request.query());

                try (DirectoryReader reader = DirectoryReader.open(memoryIndex)) {
                    IndexSearcher searcher = new IndexSearcher(reader);
                    TopDocs topDocs = searcher.search(query, request.limit());
                    
                    List<SearchMatch> matches = new ArrayList<>();
                    for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                        Document doc = searcher.doc(scoreDoc.doc);
                        String pathStr = doc.get("path");
                        // In a real implementation we would extract the snippet and line number using Lucene Highlighter
                        matches.add(new SearchMatch(Path.of(pathStr), 0, "...", scoreDoc.score));
                    }
                    
                    SearchMetadata meta = new SearchMetadata(
                        (int) topDocs.totalHits.value, 
                        topDocs.totalHits.value > request.limit(), 
                        SearchStrategy.TEXT
                    );
                    return new SearchResult(matches, meta);
                }
            } catch (Exception e) {
                throw new RuntimeException("Lucene search failed", e);
            }
        });
    }
}
