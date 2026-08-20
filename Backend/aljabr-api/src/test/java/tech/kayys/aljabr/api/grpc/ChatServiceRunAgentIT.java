package tech.kayys.aljabr.api.grpc;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.grpc.GrpcClient;
import org.junit.jupiter.api.Test;
import tech.kayys.wayang.pro.api.grpc.ChatService;
import tech.kayys.wayang.pro.api.grpc.RunAgentRequest;
import tech.kayys.wayang.pro.api.grpc.RunAgentEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class ChatServiceRunAgentIT {

    @GrpcClient
    ChatService chatClient;

    @Test
    public void testRunAgentEmitsEvents() throws InterruptedException {
        RunAgentRequest req = RunAgentRequest.newBuilder()
                .setSessionId("itest-session")
                .setPrompt("run tests")
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean sawDone = new AtomicBoolean(false);
        AtomicBoolean sawPayloadEvent = new AtomicBoolean(false);

        chatClient.runAgent(req).subscribe().with(
            event -> {
                if (event.getType().equals("DONE")) {
                    sawDone.set(true);
                    latch.countDown();
                } else {
                    if (event.getPayload() != null && !event.getPayload().isEmpty()) {
                        sawPayloadEvent.set(true);
                    }
                }
            },
            failure -> latch.countDown()
        );

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "Stream should complete with DONE event");
        assertTrue(sawDone.get(), "Should have seen DONE");
        assertTrue(sawPayloadEvent.get(), "Should have seen at least one payload-carrying AgentEvent");
    }
}
