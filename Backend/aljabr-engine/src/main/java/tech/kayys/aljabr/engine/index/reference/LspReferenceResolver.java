package tech.kayys.aljabr.engine.index.reference;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.index.FileRecord;
import tech.kayys.aljabr.engine.index.symbol.SourceRange;
import tech.kayys.aljabr.engine.index.symbol.SymbolId;
import tech.kayys.muqabalah.api.CodeTool;
import tech.kayys.muqabalah.language.LanguageTypes.CodeLocation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class LspReferenceResolver implements ReferenceResolver {

    @Inject
    CodeTool codeTool;

    @Override
    public boolean supports(FileRecord record) {
        return true; // Language agnostic through LSP
    }

    @Override
    public Uni<List<Reference>> resolveReferences(FileRecord record, SymbolId target) {
        String workspaceId = "default-workspace";
        
        // In a real system, the target SymbolId would contain its defining Location 
        // or we'd resolve it to a line/character first. 
        // For the sake of the API skeleton, we mock position 1, 1
        int line = 1;
        int character = 1;

        return codeTool.references(workspaceId, record.path(), line, character)
            .map(locations -> {
                List<Reference> references = new ArrayList<>();
                if (locations != null) {
                    for (CodeLocation loc : locations) {
                        SourceRange range = new SourceRange(
                            loc.range().start().line() + 1,
                            loc.range().end().line() + 1,
                            loc.range().start().character() + 1,
                            loc.range().end().character() + 1
                        );
                        // Using UNKNOWN since LSP just returns locations without semantic kind
                        references.add(new Reference(target, Path.of(loc.uri()), range, ReferenceKind.UNKNOWN));
                    }
                }
                return references;
            })
            .onFailure().recoverWithItem(new ArrayList<>());
    }
}
