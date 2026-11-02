package tech.kayys.cifr.engine.repo;

import java.util.List;

public record RepositorySnapshot(
    String commitHash,
    String branch,
    boolean dirty,
    List<String> changedFiles,
    List<String> modules
) {}
