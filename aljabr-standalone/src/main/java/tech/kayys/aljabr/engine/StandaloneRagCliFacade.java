package tech.kayys.aljabr.engine;

import tech.kayys.andalus.rag.core.spi.RagCliFacade;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Standalone (no CDI/Quarkus) RAG facade for the community and pro CLI.
 *
 * <p>
 * When the full Weld/Quarkus CDI container is unavailable (the common case
 * in standalone uber-jar mode), this class provides a realistic in-process
 * semantic-style search backed by TF-IDF keyword ranking across the workspace.
 *
 * <h2>Pro vs Community</h2>
 * <ul>
 * <li>Community: search is always available; ingestion is accepted
 * silently.</li>
 * <li>Pro (full CDI boot): {@code RagCliFacadeImpl} takes over with real vector
 * embeddings.</li>
 * </ul>
 *
 * <h2>Algorithm</h2>
 * <ol>
 * <li>Walk the workspace once (lazy, cached).</li>
 * <li>Extract query keywords (stop-word filtered).</li>
 * <li>For each candidate file: compute TF score for each keyword.</li>
 * <li>Return top-K files with the matching context lines (± 3 lines).</li>
 * </ol>
 */
public final class StandaloneRagCliFacade implements RagCliFacade {

    // ---------------------------------------------------------------------------
    // Configuration
    // ---------------------------------------------------------------------------
    private static final int MAX_FILE_SIZE_BYTES = 512 * 1024; // 512 KB
    private static final int TOP_K_FILES = 6;
    private static final int CONTEXT_LINES = 3;
    private static final int MAX_SNIPPET_LINES = 30;

    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            ".java", ".kt", ".scala", ".groovy",
            ".py", ".rb", ".js", ".ts", ".jsx", ".tsx",
            ".go", ".rs", ".c", ".cpp", ".h", ".hpp",
            ".cs", ".swift", ".dart",
            ".yaml", ".yml", ".json", ".xml", ".properties",
            ".md", ".txt", ".sh", ".dockerfile", ".gradle", ".pom");

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "is", "it", "in", "on", "at", "to", "for",
            "of", "and", "or", "not", "with", "this", "that", "are", "was",
            "be", "do", "by", "as", "its", "how", "what", "where", "when",
            "can", "will", "which", "from", "show", "me", "give", "list",
            "find", "get", "does", "have", "has", "my", "your", "our",
            "there", "about", "use", "used", "using", "also", "code");

    // ---------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------
    private final Path workspaceDir;
    private final boolean isPro;

    /** Lazy-built index: filename -> file path */
    private final Map<Path, String[]> fileLineCache = new ConcurrentHashMap<>();
    private volatile boolean indexed = false;

    // ---------------------------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------------------------
    public StandaloneRagCliFacade(Path workspaceDir) {
        this(workspaceDir, false);
    }

    public StandaloneRagCliFacade(Path workspaceDir, boolean isPro) {
        this.workspaceDir = workspaceDir != null ? workspaceDir.toAbsolutePath().normalize() : Path.of(".");
        this.isPro = isPro;
    }

    // ---------------------------------------------------------------------------
    // RagCliFacade API
    // ---------------------------------------------------------------------------

    @Override
    public void ingestUrl(String tenantId, String url) {
        // Lightweight: no actual embedding; just acknowledge
        System.out.println("[RAG] URL ingestion acknowledged (standalone mode). URL: " + url);
    }

    @Override
    public void query(String tenantId, String query, String collectionName,
            Consumer<String> answerConsumer) throws Exception {
        answerConsumer.accept(querySync(tenantId, query, collectionName));
    }

    @Override
    public String querySync(String tenantId, String query, String collectionName) throws Exception {
        ensureIndexed();
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedException("Interrupted");
        List<String> keywords = extractKeywords(query);
        if (keywords.isEmpty()) {
            return "No meaningful keywords found in query: \"" + query + "\". "
                    + "Try a more specific query like a class name, method, or concept.";
        }

        List<ScoredFile> ranked = rankFiles(query, keywords);
        if (ranked.isEmpty()) {
            return "No matching files found in workspace for query: \"" + query + "\".\n"
                    + "Keywords searched: " + String.join(", ", keywords) + "\n"
                    + "Try /rag index to rebuild the index, or try different terms.";
        }

        return formatResults(query, keywords, ranked);
    }

    @Override
    public void ingestWorkspace(String tenantId, Path workspace) throws Exception {
        Path target = workspace != null ? workspace : workspaceDir;
        System.out.println("[RAG] Indexing workspace: " + target);
        fileLineCache.clear();
        indexed = false;
        buildIndex(target);
        indexed = true;
        System.out.println("[RAG] Indexed " + fileLineCache.size() + " files.");
        if (isPro) {
            System.out.println("[RAG] Pro mode: use /rag <query> or the semantic_search tool to query the index.");
        } else {
            System.out.println("[RAG] Community mode: keyword-based semantic search active. "
                    + "Upgrade to Andalus Pro for true vector-embedding search.");
        }
    }

    // ---------------------------------------------------------------------------
    // Indexing
    // ---------------------------------------------------------------------------

    private synchronized void ensureIndexed() throws IOException, InterruptedException {
        if (!indexed) {
            buildIndex(workspaceDir);
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException("Interrupted");
            indexed = true;
        }
    }

    private void buildIndex(Path root) throws IOException {
        if (!Files.exists(root))
            return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (Thread.currentThread().isInterrupted())
                    return FileVisitResult.TERMINATE;
                String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                if (name.startsWith(".") || name.equals("target") || name.equals("build")
                        || name.equals("node_modules") || name.equals("__pycache__")
                        || name.equals("dist") || name.equals(".gradle")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (Thread.currentThread().isInterrupted())
                    return FileVisitResult.TERMINATE;
                if (attrs.size() > MAX_FILE_SIZE_BYTES)
                    return FileVisitResult.CONTINUE;
                String name = file.getFileName().toString().toLowerCase();
                boolean isSource = SOURCE_EXTENSIONS.stream().anyMatch(name::endsWith);
                if (!isSource)
                    return FileVisitResult.CONTINUE;
                try {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    fileLineCache.put(file, content.split("\\r?\\n", -1));
                } catch (MalformedInputException e) {
                    // skip binary
                } catch (IOException ignored) {
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // ---------------------------------------------------------------------------
    // Keyword extraction
    // ---------------------------------------------------------------------------

    private List<String> extractKeywords(String query) {
        if (query == null || query.isBlank())
            return List.of();

        List<String> keywords = new ArrayList<>();

        // Keep the raw tokens (e.g. class names without camel splitting) for exact name
        // matching
        for (String raw : query.split("[\\s,;]+")) {
            raw = raw.trim();
            if (raw.length() >= 2 && !STOP_WORDS.contains(raw.toLowerCase())) {
                keywords.add(raw); // keep original casing for filename match
            }
        }

        // Also split camelCase / PascalCase into sub-words
        String expanded = query
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");

        String[] tokens = expanded.toLowerCase().split("[^a-zA-Z0-9_]+");
        for (String tok : tokens) {
            tok = tok.trim();
            if (tok.length() >= 2 && !STOP_WORDS.contains(tok)) {
                keywords.add(tok);
            }
        }

        // Deduplicate, preserve order (case-insensitive dedup)
        List<String> deduped = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String kw : keywords) {
            if (seen.add(kw.toLowerCase())) {
                deduped.add(kw);
            }
        }
        return deduped;
    }

    // ---------------------------------------------------------------------------
    // Ranking
    // ---------------------------------------------------------------------------

    private List<ScoredFile> rankFiles(String originalQuery, List<String> keywords) {
        List<ScoredFile> results = new ArrayList<>();

        // Build two pattern sets:
        // 1. lowerKeywords — the individual camelCase sub-words for body TF scoring
        // 2. exactPatterns — the original query tokens for filename exact-match bonus
        List<String> lowerKeywords = keywords.stream()
                .map(String::toLowerCase)
                .distinct()
                .toList();
        Pattern[] bodyPatterns = lowerKeywords.stream()
                .map(kw -> Pattern.compile(Pattern.quote(kw), Pattern.CASE_INSENSITIVE))
                .toArray(Pattern[]::new);

        Pattern exactQueryPattern = null;
        if (originalQuery != null && originalQuery.length() >= 3) {
            exactQueryPattern = Pattern.compile(Pattern.quote(originalQuery.trim()), Pattern.CASE_INSENSITIVE);
        }

        for (Map.Entry<Path, String[]> entry : fileLineCache.entrySet()) {
            if (Thread.currentThread().isInterrupted())
                break;
            Path file = entry.getKey();
            String[] lines = entry.getValue();
            String fileName = file.getFileName().toString();
            String fileNameLower = fileName.toLowerCase();
            // Strip extension for matching
            String fileBase = fileNameLower.contains(".")
                    ? fileNameLower.substring(0, fileNameLower.lastIndexOf('.'))
                    : fileNameLower;

            double score = scoreFile(lines, bodyPatterns, lowerKeywords, fileBase, exactQueryPattern);
            if (score > 0) {
                List<int[]> matchedRanges = findMatchedLineRanges(lines, bodyPatterns, exactQueryPattern);
                results.add(new ScoredFile(file, score, matchedRanges));
            }
        }

        results.sort(Comparator.comparingDouble(ScoredFile::score).reversed());
        return results.subList(0, Math.min(TOP_K_FILES, results.size()));
    }

    private double scoreFile(String[] lines, Pattern[] bodyPatterns,
            List<String> lowerKeywords, String fileBase, Pattern exactQueryPattern) {
        if (lines.length == 0)
            return 0.0;

        double score = 0.0;

        // 1. Filename exact bonus — if filename contains the concatenation of all
        // keywords
        // (case-insensitive), award a massive boost. This ensures that searching for
        // "RagCliFacade" matches "StandaloneRagCliFacade" heavily.
        String allKwConcat = String.join("", lowerKeywords);
        if (allKwConcat.length() >= 3 && fileBase.contains(allKwConcat)) {
            score += 10_000.0; // unmistakeable top rank
        } else {
            // 2. Filename partial bonus — each sub-keyword that appears in the filename
            for (String kw : lowerKeywords) {
                if (fileBase.contains(kw)) {
                    score += 500.0;
                }
            }
        }

        // 3. Body TF scoring
        int keywordsHit = 0;
        for (int i = 0; i < bodyPatterns.length; i++) {
            Pattern p = bodyPatterns[i];
            int hits = 0;
            for (String line : lines) {
                if (p.matcher(line).find())
                    hits++;
            }
            if (hits > 0) {
                keywordsHit++;
                double tf = (double) hits / lines.length;
                // IDF proxy: shorter keywords are more common (lower weight)
                double idfProxy = Math.log(1.0 + 10.0 / Math.max(1, lowerKeywords.get(i).length()));
                score += tf * idfProxy * 100.0;
            }
        }

        // 4. All-keywords coverage multiplier: files that contain EVERY keyword
        // get a significant boost to push them above files that only match some
        if (bodyPatterns.length > 1 && keywordsHit == bodyPatterns.length) {
            score *= 1.5;
        }

        // 5. Exact literal query match in the body (e.g. searching for
        // `ranges.isEmpty()`)
        if (exactQueryPattern != null) {
            int exactHits = 0;
            for (String line : lines) {
                if (exactQueryPattern.matcher(line).find())
                    exactHits++;
            }
            if (exactHits > 0) {
                score += 2000.0 + (exactHits * 100.0);
            }
        }

        return score;
    }

    private List<int[]> findMatchedLineRanges(String[] lines, Pattern[] patterns, Pattern exactQueryPattern) {
        List<Integer> matchedLines = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // If we have an exact query match, only show those context lines so they aren't
            // drowned out
            if (exactQueryPattern != null && exactQueryPattern.matcher(line).find()) {
                matchedLines.add(i);
                continue;
            }

            // Otherwise match any keyword pattern
            if (exactQueryPattern == null || matchedLines.isEmpty()) {
                for (Pattern p : patterns) {
                    if (p.matcher(line).find()) {
                        matchedLines.add(i);
                        break;
                    }
                }
            }
        }
        // Group into contiguous ranges with context
        List<int[]> ranges = new ArrayList<>();
        for (int idx : matchedLines) {
            int start = Math.max(0, idx - CONTEXT_LINES);
            int end = Math.min(lines.length - 1, idx + CONTEXT_LINES);
            if (!ranges.isEmpty()) {
                int[] last = ranges.get(ranges.size() - 1);
                if (start <= last[1] + 1) {
                    last[1] = Math.max(last[1], end);
                    continue;
                }
            }
            ranges.add(new int[] { start, end });
        }

        // If no lines matched but the file scored high (e.g. filename match),
        // show the first few lines as a fallback context.
        if (ranges.isEmpty() && lines.length > 0) {
            ranges.add(new int[] { 0, Math.min(lines.length - 1, CONTEXT_LINES) });
        }

        return ranges;
    }

    // ---------------------------------------------------------------------------
    // Formatting
    // ---------------------------------------------------------------------------

    private String formatResults(String query, List<String> keywords, List<ScoredFile> ranked) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Semantic Search Results\n");
        sb.append("**Query:** ").append(query).append("\n");
        sb.append("**Keywords matched:** ").append(String.join(", ", keywords)).append("\n");
        sb.append("**Files found:** ").append(ranked.size()).append("\n");
        // Only show the community upgrade message in the community edition.
        // In Pro, the standalone facade is used as a fallback when the CDI container
        // can't be booted; the user is already on Pro.
        if (!isPro) {
            sb.append(
                    "*(Community mode: keyword-based search. Upgrade to Andalus Pro for true vector-embedding RAG.)*\n");
        }
        sb.append("\n");

        for (int i = 0; i < ranked.size(); i++) {
            ScoredFile sf = ranked.get(i);
            String relPath = workspaceDir.relativize(sf.file).toString();
            sb.append("### ").append(i + 1).append(". `").append(relPath).append("`\n");
            sb.append("*Score: ").append(String.format("%.2f", sf.score)).append("*\n\n");

            String[] lines = fileLineCache.get(sf.file);
            if (lines != null && !sf.matchedRanges.isEmpty()) {
                sb.append("```\n");
                int snippetLines = 0;
                for (int[] range : sf.matchedRanges) {
                    if (snippetLines >= MAX_SNIPPET_LINES)
                        break;
                    for (int ln = range[0]; ln <= range[1] && snippetLines < MAX_SNIPPET_LINES; ln++) {
                        sb.append(String.format("%4d | %s%n", ln + 1, lines[ln]));
                        snippetLines++;
                    }
                    if (range[1] < (lines.length - 1) && snippetLines < MAX_SNIPPET_LINES) {
                        sb.append("     ...\n");
                    }
                }
                sb.append("```\n\n");
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------------
    // Value objects
    // ---------------------------------------------------------------------------

    private record ScoredFile(Path file, double score, List<int[]> matchedRanges) {
    }
}
