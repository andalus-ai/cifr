package tech.kayys.cifr.engine.tool.analysis;

import java.util.List;

public record FormatResult(String language, int totalFiles, int formattedCount, int failedCount, List<String> errors) {}
