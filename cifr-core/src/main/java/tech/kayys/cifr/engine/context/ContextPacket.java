package tech.kayys.cifr.engine.context;

import java.util.List;

public record ContextPacket(
    String taskId,
    List<ContextSection> sections,
    int totalEstimatedTokens,
    String renderedPrompt
) {
    public static ContextPacket of(String taskId, List<ContextSection> sections) {
        StringBuilder sb = new StringBuilder();
        int totalTokens = 0;

        for (ContextSection section : sections) {
            sb.append("### ").append(section.header()).append("\n");
            sb.append(section.content().trim()).append("\n\n");
            totalTokens += section.estimatedTokens();
        }

        return new ContextPacket(taskId, List.copyOf(sections), totalTokens, sb.toString().trim());
    }

    public String toPrompt() {
        return renderedPrompt;
    }
}
