package tech.kayys.aljabr.engine.knowledge.graph;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public sealed interface RepositoryNode {
    String id();
    String name();
    NodeType type();

    record Module(String id, String name, Set<Package> packages) implements RepositoryNode {
        public Module {
            packages = packages != null ? Set.copyOf(packages) : Set.of();
        }
        @Override public NodeType type() { return NodeType.MODULE; }
    }

    record Package(String id, String name, Set<Class> classes) implements RepositoryNode {
        public Package {
            classes = classes != null ? Set.copyOf(classes) : Set.of();
        }
        @Override public NodeType type() { return NodeType.PACKAGE; }
    }

    record Class(String id, String name, ClassKind kind, Set<Method> methods, Set<Field> fields, Set<Reference> references) implements RepositoryNode {
        public Class {
            kind = kind != null ? kind : ClassKind.CLASS;
            methods = methods != null ? Set.copyOf(methods) : Set.of();
            fields = fields != null ? Set.copyOf(fields) : Set.of();
            references = references != null ? Set.copyOf(references) : Set.of();
        }
        @Override public NodeType type() { return NodeType.CLASS; }
    }

    record Method(String id, String name, String signature, Set<Reference> references) implements RepositoryNode {
        public Method {
            references = references != null ? Set.copyOf(references) : Set.of();
        }
        @Override public NodeType type() { return NodeType.METHOD; }
    }

    record Field(String id, String name, String fieldType) implements RepositoryNode {
        @Override public NodeType type() { return NodeType.FIELD; }
    }

    record Reference(String id, String name, String targetId, ReferenceKind kind) implements RepositoryNode {
        public Reference(String id, String targetId, ReferenceKind kind) {
            this(id, id, targetId, kind);
        }
        @Override public NodeType type() { return NodeType.REFERENCE; }
    }

    record Test(String id, String name, String filePath) implements RepositoryNode {
        @Override public NodeType type() { return NodeType.TEST; }
    }

    record Api(String id, String name, ApiKind kind) implements RepositoryNode {
        @Override public NodeType type() { return NodeType.API; }
    }

    record Configuration(String id, String name, Map<String, Object> values) implements RepositoryNode {
        public Configuration {
            values = values != null ? Map.copyOf(values) : Map.of();
        }
        @Override public NodeType type() { return NodeType.CONFIG; }
    }

    record DatabaseSchema(String id, String name, Set<Table> tables) implements RepositoryNode {
        public DatabaseSchema {
            tables = tables != null ? Set.copyOf(tables) : Set.of();
        }
        @Override public NodeType type() { return NodeType.DATABASE; }
    }

    record Table(String id, String name, Set<Column> columns) implements RepositoryNode {
        public Table {
            columns = columns != null ? Set.copyOf(columns) : Set.of();
        }
        @Override public NodeType type() { return NodeType.TABLE; }
    }

    record Column(String id, String name, String columnType, boolean nullable) implements RepositoryNode {
        @Override public NodeType type() { return NodeType.COLUMN; }
    }

    record GitCommit(String id, String name, String hash, String message, Instant date, Set<String> changedFiles) implements RepositoryNode {
        public GitCommit(String id, String hash, String message, Instant date, Set<String> changedFiles) {
            this(id, hash, hash, message, date, changedFiles != null ? Set.copyOf(changedFiles) : Set.of());
        }
        @Override public NodeType type() { return NodeType.GIT_COMMIT; }
    }

    record BuildTarget(String id, String name, BuildSystem system) implements RepositoryNode {
        @Override public NodeType type() { return NodeType.BUILD_TARGET; }
    }
}
