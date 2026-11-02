package tech.kayys.cifr.engine.patch.transaction;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;
import java.util.Optional;

public interface SymbolResolver {

    Uni<Optional<ResolvedSymbol>> resolve(String symbolReference, Path workspaceRoot);
}
