package tech.kayys.cifr.engine.index.reference;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.index.FileRecord;
import tech.kayys.cifr.engine.index.symbol.SourceRange;
import tech.kayys.cifr.engine.index.symbol.SymbolId;
import tech.kayys.muqabala.api.CodeTool;
import tech.kayys.muqabala.language.LanguageTypes.CodeLocation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class LspReferenceResolver implements ReferenceResolver {

    @Inject
    CodeTool codeTool;

    @Override
    public boolean supports(FileRecord record) {
        return true;
    }

    @Override
    public Uni<List<Reference>> resolveReferences(FileRecord record, SymbolId target) {
        // Keep the existing position contract until SymbolId carries a real
        // definition location. The workspace itself must never be guessed.
        int line = 1;
        int character = 1;

        return codeTool.references(record.workspaceId(), record.path(), line, character)
                .map(locations -> {
                    List<Reference> references = new ArrayList<>();
                    if (locations != null) {
                        for (CodeLocation loc : locations) {
                            SourceRange range = new SourceRange(
                                    loc.range().start().line() + 1,
                                    loc.range().end().line() + 1,
                                    loc.range().start().character() + 1,
                                    loc.range().end().character() + 1);
                            references.add(new Reference(target, Path.of(loc.uri()), range, ReferenceKind.UNKNOWN));
                        }
                    }
                    return references;
                })
                .onFailure().recoverWithItem(new ArrayList<>());
    }
}
