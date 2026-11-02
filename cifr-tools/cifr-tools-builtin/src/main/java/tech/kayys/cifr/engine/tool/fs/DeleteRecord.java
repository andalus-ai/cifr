package tech.kayys.cifr.engine.tool.fs;

import tech.kayys.cifr.engine.tool.ToolResult;
import java.util.List;

public record DeleteRecord(String path, boolean isDirectory, int fileCount, long sizeBytes) {}
