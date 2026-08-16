package tech.kayys.aljabr.engine.index;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.test.Mock;
import tech.kayys.muqabalah.api.CodeTool;
import tech.kayys.muqabalah.document.TextEdit;
import tech.kayys.muqabalah.language.LanguageTypes.*;
import tech.kayys.muqabalah.document.Position;
import tech.kayys.muqabalah.document.Range;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

@Mock
@ApplicationScoped
public class MockCodeTool implements CodeTool {

    @Override
    public Uni<List<SymbolEntry>> documentSymbols(String workspaceId, Path file) {
        // Return a mock symbol representing a method
        Range range = new Range(new Position(10, 0), new Position(20, 0));
        SymbolEntry entry = new SymbolEntry("mockMethod", "method", file.toUri(), range, "MockClass", null);
        return Uni.createFrom().item(List.of(entry));
    }

    @Override
    public Uni<List<CodeLocation>> references(String workspaceId, Path file, int line, int character) {
        // Return a mock location representing where a symbol is referenced
        Range range = new Range(new Position(5, 0), new Position(5, 10));
        CodeLocation location = new CodeLocation(file.toUri(), range);
        return Uni.createFrom().item(List.of(location));
    }

    // --- Unused mocks for the rest of the interface ---

    @Override
    public Uni<ProjectHandle> openProject(String workspaceId, Path root) {
        return Uni.createFrom().item(new ProjectHandle(root.toString(), workspaceId));
    }

    @Override
    public Uni<DocumentHandle> open(String workspaceId, Path file) {
        return Uni.createFrom().item(new DocumentHandle(workspaceId, file.toString(), 1));
    }

    @Override
    public Uni<Void> close(String workspaceId, Path file) {
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<String> read(String workspaceId, Path file) {
        return Uni.createFrom().item("");
    }

    @Override
    public Uni<Void> edit(String workspaceId, Path file, String newFullText) {
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Void> save(String workspaceId, Path file) {
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<CompletionResult> complete(String workspaceId, Path file, int line, int character) {
        return Uni.createFrom().item(new CompletionResult(List.of(), false));
    }

    @Override
    public Uni<HoverResult> hover(String workspaceId, Path file, int line, int character) {
        return Uni.createFrom().item(new HoverResult("Mock", null));
    }

    @Override
    public Uni<List<CodeLocation>> definition(String workspaceId, Path file, int line, int character) {
        return Uni.createFrom().item(List.of());
    }

    @Override
    public Uni<WorkspaceEdit> rename(String workspaceId, Path file, int line, int character, String newName) {
        return Uni.createFrom().nullItem();
    }

    @Override
    public Uni<List<CodeAction>> codeActions(String workspaceId, Path file, tech.kayys.muqabalah.document.Range range) {
        return Uni.createFrom().item(List.of());
    }

    @Override
    public Uni<Void> fixDiagnostics(String workspaceId, Path file) {
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<List<TextEdit>> format(String workspaceId, Path file) {
        return Uni.createFrom().item(List.of());
    }

    @Override
    public Uni<List<SymbolEntry>> workspaceSymbols(String workspaceId, String query) {
        return Uni.createFrom().item(List.of());
    }

    @Override
    public Uni<List<CodeLocation>> findImplementations(String workspaceId, Path file, int line, int character) {
        return Uni.createFrom().item(List.of());
    }

    @Override
    public Uni<CallHierarchy> findCallHierarchy(String workspaceId, Path file, int line, int character) {
        return Uni.createFrom().nullItem();
    }

    @Override
    public Uni<SemanticTokens> semanticTokens(String workspaceId, Path file) {
        return Uni.createFrom().item(new SemanticTokens(List.of()));
    }
}
