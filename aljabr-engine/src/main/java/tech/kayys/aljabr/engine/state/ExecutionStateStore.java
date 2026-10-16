package tech.kayys.aljabr.engine.state;

import tech.kayys.aljabr.api.agent.AgentPhase;
import tech.kayys.andalus.agent.AgentResponse;

import java.util.Map;
import java.util.Optional;

public interface ExecutionStateStore {
    
    Optional<ExecutionState> get(String executionId);
    
    ExecutionState transition(String executionId, AgentPhase phase, Map<String, Object> metadata);
    
    void checkpoint(ExecutionState state);
    
    void complete(String executionId, AgentResponse response);
    
    void fail(String executionId, Throwable error);
}
