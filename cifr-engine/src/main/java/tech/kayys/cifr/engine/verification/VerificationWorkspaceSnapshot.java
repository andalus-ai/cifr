package tech.kayys.cifr.engine.verification;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/**
 * Creates a bounded, symlink-free copy of a workspace for isolated verification.
 *
 * <p>The returned directory is disposable staging data, never the user's live
 * workspace. Callers must close the snapshot after the container has stopped.</p>
 */
public final class VerificationWorkspaceSnapshot implements AutoCloseable {

    private final Path root;
    private boolean closed;

    private VerificationWorkspaceSnapshot(Path root) {
        this.root = root;
    }

    public Path root() {
        if (closed) {
            throw new IllegalStateException("workspace snapshot is already closed");
        }
        return root;
    }

    public static VerificationWorkspaceSnapshot create(Path workspace, Limits limits) throws IOException {
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(limits, "limits");

        Path source = workspace.toRealPath();
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Workspace must be a real directory");
        }

        Path staging = Files.createTempDirectory("cifr-verification-");
        Counter counter = new Counter(limits);
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs)
                        throws IOException {
                    if (attrs.isSymbolicLink() || !attrs.isDirectory()) {
                        throw new IOException("Unsupported workspace entry: " + directory);
                    }
                    if (!directory.equals(source) && directory.getFileName().toString().equals(".git")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    Path target = staging.resolve(source.relativize(directory).toString()).normalize();
                    if (!target.startsWith(staging)) {
                        throw new IOException("Workspace path escapes staging directory");
                    }
                    Files.createDirectories(target);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (attrs.isSymbolicLink() || !attrs.isRegularFile()) {
                        throw new IOException("Unsupported workspace entry: " + file);
                    }
                    if (file.getFileName().toString().equals(".git")) {
                        return FileVisitResult.CONTINUE;
                    }
                    counter.add(attrs.size());
                    Path target = staging.resolve(source.relativize(file).toString()).normalize();
                    if (!target.startsWith(staging)) {
                        throw new IOException("Workspace path escapes staging directory");
                    }
                    Files.copy(file, target, LinkOption.NOFOLLOW_LINKS);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException error) throws IOException {
                    throw new IOException("Cannot safely snapshot workspace entry: " + file, error);
                }
            });
            return new VerificationWorkspaceSnapshot(staging);
        } catch (IOException | RuntimeException failure) {
            deleteTree(staging);
            throw failure;
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            deleteTree(root);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) {
                    throw error;
                }
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static final class Counter {
        private final Limits limits;
        private long bytes;
        private int files;

        private Counter(Limits limits) {
            this.limits = limits;
        }

        private void add(long size) throws IOException {
            files++;
            if (files > limits.maxFiles()) {
                throw new IOException("Workspace snapshot exceeds file-count limit");
            }
            try {
                bytes = Math.addExact(bytes, size);
            } catch (ArithmeticException overflow) {
                throw new IOException("Workspace snapshot size overflow", overflow);
            }
            if (bytes > limits.maxBytes()) {
                throw new IOException("Workspace snapshot exceeds byte limit");
            }
        }
    }

    public record Limits(int maxFiles, long maxBytes) {
        public Limits {
            if (maxFiles < 1 || maxBytes < 1) {
                throw new IllegalArgumentException("Snapshot limits must be positive");
            }
        }

        public static Limits conservativeDefaults() {
            return new Limits(100_000, 2L * 1024 * 1024 * 1024);
        }
    }
}
