package tech.kayys.cifr.engine.loop;

import tech.kayys.cifr.engine.repo.ChangeSet;
import tech.kayys.cifr.engine.repo.RepositorySnapshot;
import tech.kayys.cifr.engine.repo.ChangeRecord;
import java.util.ArrayList;
import java.util.List;

public class CodingState {
    private final CodingTask task;
    private RepositorySnapshot repository;
    private CodingPlan plan;
    private ChangeSet changes;
    private VerificationState verification;
    private int iteration;
    private final List<ChangeRecord> changeHistory;
    private tech.kayys.cifr.api.agent.CifrState cifrState;
    private tech.kayys.cifr.engine.phase.TaskExecutionResult lastTaskResult;

    public CodingState(CodingTask task) {
        this.task = task;
        this.changes = new ChangeSet(List.of());
        this.verification = VerificationState.initial();
        this.iteration = 1;
        this.changeHistory = new ArrayList<>();
    }

    public CodingTask getTask() {
        return task;
    }

    public RepositorySnapshot getRepository() {
        return repository;
    }

    public void setRepository(RepositorySnapshot repository) {
        this.repository = repository;
    }

    public CodingPlan getPlan() {
        return plan;
    }

    public void setPlan(CodingPlan plan) {
        this.plan = plan;
    }

    public ChangeSet getChanges() {
        return changes;
    }

    public void setChanges(ChangeSet changes) {
        this.changes = changes;
    }

    public VerificationState getVerification() {
        return verification;
    }

    public void setVerification(VerificationState verification) {
        this.verification = verification;
    }

    public int getIteration() {
        return iteration;
    }

    public void incrementIteration() {
        this.iteration++;
    }

    public void addChangeRecord(ChangeRecord record) {
        this.changeHistory.add(record);
    }

    public List<ChangeRecord> getChangeHistory() {
        return List.copyOf(changeHistory);
    }

    public tech.kayys.cifr.api.agent.CifrState getCifrState() {
        return cifrState;
    }

    public void setCifrState(tech.kayys.cifr.api.agent.CifrState cifrState) {
        this.cifrState = cifrState;
    }

    public tech.kayys.cifr.engine.phase.TaskExecutionResult getLastTaskResult() {
        return lastTaskResult;
    }

    public void setLastTaskResult(tech.kayys.cifr.engine.phase.TaskExecutionResult lastTaskResult) {
        this.lastTaskResult = lastTaskResult;
    }
}
