package tech.kayys.cifr.engine.tool.diff;

import java.util.List;

public record DiffBlock(
    int leftStart, int leftEnd, int rightStart, int rightEnd, 
    List<String> leftContent, List<String> rightContent
) {}
