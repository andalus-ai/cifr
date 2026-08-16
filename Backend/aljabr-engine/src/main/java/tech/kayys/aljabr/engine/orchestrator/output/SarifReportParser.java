package tech.kayys.aljabr.engine.orchestrator.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SarifReportParser {

    private static final Logger LOG = Logger.getLogger(SarifReportParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<ExecutionDiagnostic> parse(String jsonContent) {
        List<ExecutionDiagnostic> diagnostics = new ArrayList<>();
        if (jsonContent == null || jsonContent.isBlank()) {
            return diagnostics;
        }

        try {
            JsonNode root = MAPPER.readTree(jsonContent);
            JsonNode runs = root.path("runs");
            if (runs.isArray()) {
                for (JsonNode run : runs) {
                    JsonNode results = run.path("results");
                    if (results.isArray()) {
                        for (JsonNode result : results) {
                            String ruleId = result.path("ruleId").asText("SONAR_RULE");
                            String level = result.path("level").asText("error").toUpperCase();
                            String message = result.path("message").path("text").asText();

                            Path filePath = null;
                            int line = 1;

                            JsonNode locations = result.path("locations");
                            if (locations.isArray() && !locations.isEmpty()) {
                                JsonNode phys = locations.get(0).path("physicalLocation");
                                String uri = phys.path("artifactLocation").path("uri").asText("");
                                if (!uri.isBlank()) {
                                    filePath = Path.of(uri);
                                }
                                line = phys.path("region").path("startLine").asInt(1);
                            }

                            String category = "STATIC_ANALYSIS";
                            if (ruleId.toLowerCase().contains("cwe") || ruleId.toLowerCase().contains("sec") || message.toLowerCase().contains("vulnerability")) {
                                category = "SECURITY";
                            } else if (ruleId.toLowerCase().contains("bug") || message.toLowerCase().contains("null") || message.toLowerCase().contains("leak")) {
                                category = "BUG";
                            }

                            String severity = level.equalsIgnoreCase("error") ? "ERROR" : "WARNING";
                            diagnostics.add(new ExecutionDiagnostic(Optional.ofNullable(filePath), line, "[" + ruleId + "] " + message, severity, category));
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warnf("Failed to parse SARIF report JSON: %s", e.getMessage());
        }

        return diagnostics;
    }
}
