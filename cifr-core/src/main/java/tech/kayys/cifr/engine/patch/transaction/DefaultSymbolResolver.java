package tech.kayys.cifr.engine.patch.transaction;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
public class DefaultSymbolResolver implements SymbolResolver {

    @Override
    public Uni<Optional<ResolvedSymbol>> resolve(String symbolReference, Path workspaceRoot) {
        return Uni.createFrom().item(() -> {
            if (symbolReference == null || symbolReference.isBlank() || workspaceRoot == null || !Files.exists(workspaceRoot)) {
                return Optional.empty();
            }

            // Simple heuristic symbol lookup in workspace files
            String simpleName = symbolReference.contains(".")
                ? symbolReference.substring(symbolReference.lastIndexOf('.') + 1)
                : symbolReference;

            try (Stream<Path> stream = Files.walk(workspaceRoot)) {
                Optional<Path> match = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".rs") || p.toString().endsWith(".ts") || p.toString().endsWith(".py"))
                    .filter(p -> {
                        try {
                            String content = Files.readString(p);
                            return content.contains(simpleName);
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .findFirst();

                if (match.isPresent()) {
                    Path file = match.get();
                    List<String> lines = Files.readAllLines(file);
                    int startLine = 1;
                    for (int i = 0; i < lines.size(); i++) {
                        if (lines.get(i).contains(simpleName)) {
                            startLine = i + 1;
                            break;
                        }
                    }
                    String hash = Integer.toHexString(Files.readString(file).hashCode());
                    return Optional.of(new ResolvedSymbol(symbolReference, simpleName, file, startLine, startLine + 10, hash));
                }
            } catch (Exception e) {
                // fall through
            }

            return Optional.empty();
        });
    }
}
