package tech.kayys.cifr.engine.loop;

import java.nio.file.Path;

public record CodingTask(
    String id,
    String sessionId,
    Path workspace,
    String request,
    CodingIntent intent,
    CodingScope scope,
    CodingPolicy policy,
    String idempotencyKey,
    int attempt,
    TaskDeliveryState deliveryState,
    String remoteExecutionReference,
    OrchestrationStrategy orchestrationStrategy
) {}
