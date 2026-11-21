package tech.kayys.cifr.engine.index.symbol;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DefaultSymbolIndexTest {
    @Test
    void resolvesSameSymbolNameWithinTheRequestedWorkspace() {
        DefaultSymbolIndex index = new DefaultSymbolIndex();
        SymbolId id = SymbolId.of("example.Widget");
        SymbolInfo first = new SymbolInfo(id, "Widget", SymbolKind.CLASS, Path.of("src/Widget.java"),
                new SourceRange(1, 1, 0, 6), Optional.empty(), "workspace-a");
        SymbolInfo second = new SymbolInfo(id, "Widget", SymbolKind.CLASS, Path.of("src/Widget.java"),
                new SourceRange(1, 1, 0, 6), Optional.empty(), "workspace-b");
        index.registerSymbol(first);
        index.registerSymbol(second);

        assertEquals(first, index.getSymbol(id, "workspace-a").await().indefinitely());
        assertEquals(second, index.getSymbol(id, "workspace-b").await().indefinitely());
        assertNull(index.getSymbol(id).await().indefinitely());
    }
}
