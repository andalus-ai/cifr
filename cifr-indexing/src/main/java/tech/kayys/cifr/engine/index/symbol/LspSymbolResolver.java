package tech.kayys.cifr.engine.index.symbol;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.index.FileRecord;
import tech.kayys.muqabala.api.CodeTool;
import tech.kayys.muqabala.language.LanguageTypes.SymbolEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class LspSymbolResolver implements SymbolResolver {

    @Inject
    CodeTool codeTool;

    @Override
    public boolean supports(FileRecord record) {
        return true;
    }

    @Override
    public Uni<List<SymbolInfo>> resolve(FileRecord record) {
        return codeTool.documentSymbols(record.workspaceId(), record.path())
                .map(entries -> {
                    List<SymbolInfo> symbols = new ArrayList<>();
                    if (entries != null) {
                        for (SymbolEntry entry : entries) {
                            processSymbolEntry(entry, record, null, symbols);
                        }
                    }
                    return symbols;
                })
                .onFailure().recoverWithItem(new ArrayList<>());
    }

    private void processSymbolEntry(SymbolEntry entry, FileRecord record, SymbolId parentId, List<SymbolInfo> symbols) {
        SymbolKind kind = mapKindString(entry.kind());
        String name = entry.name();
        String qualified = parentId != null ? parentId.qualifiedName() + "." + name : name;
        if (parentId == null && entry.containerName() != null && !entry.containerName().isBlank()) {
            qualified = entry.containerName() + "." + name;
        }

        SymbolId id = new SymbolId(qualified, name, "hash");
        SourceRange range = new SourceRange(
                entry.range().start().line() + 1,
                entry.range().end().line() + 1,
                entry.range().start().character() + 1,
                entry.range().end().character() + 1);

        SymbolInfo info = new SymbolInfo(id, name, kind, record.path(), range, Optional.ofNullable(parentId));
        symbols.add(info);

        if (entry.children() != null) {
            for (SymbolEntry child : entry.children()) {
                processSymbolEntry(child, record, id, symbols);
            }
        }
    }

    private SymbolKind mapKindString(String kindString) {
        if (kindString == null) return SymbolKind.VARIABLE;
        return switch (kindString.toLowerCase()) {
            case "class" -> SymbolKind.CLASS;
            case "interface" -> SymbolKind.INTERFACE;
            case "enum" -> SymbolKind.ENUM;
            case "method" -> SymbolKind.METHOD;
            case "function" -> SymbolKind.FUNCTION;
            case "field" -> SymbolKind.FIELD;
            case "property" -> SymbolKind.PROPERTY;
            case "variable" -> SymbolKind.VARIABLE;
            case "constructor" -> SymbolKind.CONSTRUCTOR;
            case "module", "namespace", "package" -> SymbolKind.MODULE;
            default -> SymbolKind.VARIABLE;
        };
    }
}
