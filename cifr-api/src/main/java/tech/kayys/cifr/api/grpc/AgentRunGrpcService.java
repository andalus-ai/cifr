package tech.kayys.cifr.engine.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.proto.agentrun.AgentRunMessage;
import tech.kayys.cifr.proto.agentrun.AgentRunServiceGrpc;
import tech.kayys.cifr.proto.agentrun.StartRunRequest;
import tech.kayys.cifr.engine.event.DefaultEventLedger;
import tech.kayys.cifr.api.agent.AgentEvent;
import tech.kayys.cifr.orchestration.CoderOrchestrator;
import tech.kayys.andalus.agent.AgentRequest;
import io.grpc.stub.StreamObserver;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.grpc.GrpcService;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.function.Consumer;

@GrpcService
@Singleton
public class AgentRunGrpcService extends AgentRunServiceGrpc.AgentRunServiceImplBase {

    @Inject
    DefaultEventLedger eventLedger;

    @Inject
    CoderOrchestrator orchestrator;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void startRun(StartRunRequest request, StreamObserver<AgentRunMessage> responseObserver) {
        Consumer<AgentEvent> listener = ev -> {
            try {
                Map data = mapper.convertValue(ev, Map.class);
                AgentRunMessage.Builder b = AgentRunMessage.newBuilder().setEvent(ev.getClass().getSimpleName());
                if (data != null) {
                    for (Object e : data.entrySet()) {
                        Map.Entry entry = (Map.Entry) e;
                        b.putData(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                    }
                }
                responseObserver.onNext(b.build());
            } catch (Exception ex) {
                // ignore
            }
        };

        eventLedger.registerListener(listener);

        AgentRequest ar = AgentRequest.builder()
                .sessionId(request.getSessionId())
                .content(request.getRequest())
                .build();

        orchestrator.execute(ar).subscribe().with(resp -> {
            responseObserver.onCompleted();
            eventLedger.unregisterListener(listener);
        }, err -> {
            responseObserver.onError(err);
            eventLedger.unregisterListener(listener);
        });
    }
}
