package tech.kayys.aljabr.engine.tool.fs;

import tech.kayys.aljabr.engine.tool.ToolResult;
import java.util.List;

public record DeleteRecord(String path, boolean isDirectory, int fileCount, long sizeBytes) {}
