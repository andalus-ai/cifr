package tech.kayys.aljabr.engine.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe implementation of {@link ExecutionBudgetTracker}.
 */
public class DefaultExecutionBudgetTracker implements ExecutionBudgetTracker {

    private final Instant startedAt = Instant.now();
    private final AtomicInteger stepsUsed = new AtomicInteger(0);
    private final AtomicInteger toolCallsUsed = new AtomicInteger(0);
    private final AtomicInteger modelCallsUsed = new AtomicInteger(0);
    private final AtomicInteger replansUsed = new AtomicInteger(0);
    private final AtomicLong outputTokensUsed = new AtomicLong(0L);

    @Override
    public void recordStep() {
        stepsUsed.incrementAndGet();
    }

    @Override
    public void recordToolCall() {
        toolCallsUsed.incrementAndGet();
    }

    @Override
    public void recordModelCall() {
        modelCallsUsed.incrementAndGet();
    }

    @Override
    public void recordReplan() {
        replansUsed.incrementAndGet();
    }

    @Override
    public void recordOutputTokens(long tokens) {
        if (tokens > 0) {
            outputTokensUsed.addAndGet(tokens);
        }
    }

    @Override
    public BudgetStatus evaluate(ExecutionBudget budget) {
        if (budget == null) {
            return BudgetStatus.healthy(
                stepsUsed.get(), toolCallsUsed.get(), modelCallsUsed.get(),
                replansUsed.get(), Duration.between(startedAt, Instant.now()), outputTokensUsed.get()
            );
        }

        Duration elapsed = Duration.between(startedAt, Instant.now());

        if (stepsUsed.get() >= budget.maxSteps()) {
            return BudgetStatus.exhausted(stepsUsed.get(), toolCallsUsed.get(), modelCallsUsed.get(),
                replansUsed.get(), elapsed, outputTokensUsed.get(), "Max execution steps exceeded: " + budget.maxSteps());
        }
        if (toolCallsUsed.get() >= budget.maxToolCalls()) {
            return BudgetStatus.exhausted(stepsUsed.get(), toolCallsUsed.get(), modelCallsUsed.get(),
                replansUsed.get(), elapsed, outputTokensUsed.get(), "Max tool calls exceeded: " + budget.maxToolCalls());
        }
        if (modelCallsUsed.get() >= budget.maxModelCalls()) {
            return BudgetStatus.exhausted(stepsUsed.get(), toolCallsUsed.get(), modelCallsUsed.get(),
                replansUsed.get(), elapsed, outputTokensUsed.get(), "Max model calls exceeded: " + budget.maxModelCalls());
        }
        if (replansUsed.get() >= budget.maxReplans()) {
            return BudgetStatus.exhausted(stepsUsed.get(), toolCallsUsed.get(), modelCallsUsed.get(),
                replansUsed.get(), elapsed, outputTokensUsed.get(), "Max replan iterations exceeded: " + budget.maxReplans());
        }
        if (budget.maxDuration() != null && elapsed.compareTo(budget.maxDuration()) >= 0) {
            return BudgetStatus.exhausted(stepsUsed.get(), toolCallsUsed.get(), modelCallsUsed.get(),
                replansUsed.get(), elapsed, outputTokensUsed.get(), "Max execution duration exceeded: " + budget.maxDuration());
        }
        if (outputTokensUsed.get() >= budget.maxOutputTokens()) {
            return BudgetStatus.exhausted(stepsUsed.get(), toolCallsUsed.get(), modelCallsUsed.get(),
                replansUsed.get(), elapsed, outputTokensUsed.get(), "Max output tokens exceeded: " + budget.maxOutputTokens());
        }

        return BudgetStatus.healthy(
            stepsUsed.get(), toolCallsUsed.get(), modelCallsUsed.get(),
            replansUsed.get(), elapsed, outputTokensUsed.get()
        );
    }
}
